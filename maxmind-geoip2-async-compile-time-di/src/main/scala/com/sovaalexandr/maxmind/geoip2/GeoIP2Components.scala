package com.sovaalexandr.maxmind.geoip2

import java.io.File
import java.time.LocalDateTime

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.dispatch.MessageDispatcher
import akka.pattern.ask
import akka.routing.FromConfig
import akka.stream.Materializer
import akka.util.Timeout
import com.sovaalexandr.maxmind.geoip2.database._
import com.sovaalexandr.maxmind.geoip2.database.actualization.DurationToFirstWednesdayOfNextMonth
import com.sovaalexandr.maxmind.geoip2.database.download._
import com.sovaalexandr.maxmind.geoip2.database.download.headers.{RememberedAtSyncCacheHeaders, RememberedEtagLastModifiedHeaders}
import com.typesafe.config.Config
import play.api.cache.SyncCacheApi
import play.api.libs.ws.StandaloneWSClient

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration, _}

trait GeoIP2Components {
  protected val actorSystem: ActorSystem
  protected val config: Config
  protected val ws: StandaloneWSClient
  protected val cache: SyncCacheApi

  protected def compressedBy(config: Config): CompressedBy = HttpDatabaseFetch.CompressedBy.withName(config.getString("geolocation.geoip2db.compression.type")) match {
    case HttpDatabaseFetch.CompressedBy.GZIP => new CompressedByGZIP(config.getBytes("geolocation.geoip2db.compression.chunkSize").toInt)
    case HttpDatabaseFetch.CompressedBy.DEFLATE => new CompressedByDEFLATE(config.getBytes("geolocation.geoip2db.compression.chunkSize").toInt)
    case HttpDatabaseFetch.CompressedBy.NONE => CompressedByNONE
  }

  protected def eTagFilter(cache: SyncCacheApi, actorSystem: ActorSystem, configuration: Config): RememberedHeadersFilter = {
    implicit val ec: MessageDispatcher = actorSystem.dispatchers.lookup(configuration.getString("geolocation.geoip2db.download.dispatcherName"))
    new RememberedHeadersFilter(new RememberedEtagLastModifiedHeaders(new RememberedAtSyncCacheHeaders(
      cache, // Maybe it should be possible to specify headers placement from config?..
      Duration(configuration.getString("geolocation.geoip2db.download.headers.validFor"))
    )))
  }

  protected def dbFile(configuration: Config): File = new File(configuration.getString("geolocation.geoip2db.dbFile"))

  protected def downloadRequestProviderSettings(configuration: Config): DownloadRequest.Settings =
    DownloadRequest.Settings(configuration.getString("geolocation.geoip2db.dbUrl"))

  protected def downloadRequestProvider(ws: StandaloneWSClient, settings: DownloadRequest.Settings, filter: RememberedHeadersFilter): DownloadRequest =
    new DownloadRequest(ws, settings, filter)

  protected def httpDatabaseFetch(requestProvider: DownloadRequest, decompressed: CompressedBy)(implicit m: Materializer): DatabaseFetch =
    new HttpDatabaseFetch(requestProvider, decompressed)

  protected def durationToFirstWednesdayOfNextMonth(): LocalDateTime => FiniteDuration = new DurationToFirstWednesdayOfNextMonth()

  protected def databaseFileProviderSettings(configuration: Config): DatabaseFileProvider.Settings =
    DatabaseFileProvider.Settings(configuration.getString("geolocation.geoip2db.databaseFileProviderPersistenceId"))

  protected def databaseFileProviderProps(fetch: DatabaseFetch, actualizeTimeout: LocalDateTime => FiniteDuration, file: File): Props =
    DatabaseFileProvider.props(fetch, actualizeTimeout, file, databaseFileProviderSettings(config))

  protected def databaseReaderProviderProps(configuration: Config): Props = {
    val locales = configuration.getStringList("play.i18n.langs")
    DatabaseReaderProvider.props(DatabaseReaderProvider.Settings(locales))
  }

  protected def databaseReaderActorProps(configuration: Config): Props =
    if (1 == configuration.getInt("geolocation.geoip2db.instances")) DatabaseReaderActor.props() else FromConfig.props(DatabaseReaderActor.props())

  protected def geoIP2DBSupervisorProps(fileProvider: Props, readerProvider: Props, reader: Props): Props =
    GeoIP2DBSupervisor.props(fileProvider, readerProvider, reader)

  protected def geoIP2DBSupervisor(props: Props, actorSystem: ActorSystem): ActorRef =
    actorSystem.actorOf(props, "geolocation-supervisor")

  def databaseReaderRef()(implicit m: Materializer): ActorRef = {
    val supervisor = geoIP2DBSupervisor(
      geoIP2DBSupervisorProps(
        databaseFileProviderProps(
          httpDatabaseFetch(
            downloadRequestProvider(
              ws,
              downloadRequestProviderSettings(config),
              eTagFilter(cache, actorSystem, config)
            ),
            compressedBy(config)
          ),
          durationToFirstWednesdayOfNextMonth(),
          dbFile(config)
        ),
        databaseReaderProviderProps(config),
        databaseReaderActorProps(config)
      ),
      actorSystem
    )
    val fiveSeconds = 5.seconds
    implicit val timeout: Timeout = Timeout(fiveSeconds)
    Await.result((supervisor ? GeoIP2DBSupervisor.Reader).mapTo[ActorRef], fiveSeconds)
  }
}
