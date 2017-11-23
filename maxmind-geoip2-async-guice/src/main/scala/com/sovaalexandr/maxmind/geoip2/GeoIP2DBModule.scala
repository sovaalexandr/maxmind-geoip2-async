package com.sovaalexandr.maxmind.geoip2

import java.io.File
import java.time.LocalDateTime
import javax.inject.{Inject, Named}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.dispatch.MessageDispatcher
import akka.pattern.ask
import akka.routing.FromConfig
import akka.stream.Materializer
import akka.util.Timeout
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, Provider, Provides}
import com.sovaalexandr.maxmind.geoip2.database._
import com.sovaalexandr.maxmind.geoip2.database.actualization.DurationToFirstWednesdayOfNextMonth
import com.sovaalexandr.maxmind.geoip2.database.download._
import com.sovaalexandr.maxmind.geoip2.database.download.headers.{RememberedAtSyncCacheHeaders, RememberedEtagLastModifiedHeaders}
import com.typesafe.config.Config
import play.Environment
import play.api.cache.SyncCacheApi
import play.api.libs.ws.StandaloneWSClient

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration, _}

class GeoIP2DBModule(environment: Environment, configuration: Config) extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[ActorRef]).annotatedWith(Names.named("geolocation")).toProvider(classOf[DatabaseReaderActorProvider]).asEagerSingleton()
  }

  @Provides
  def compressedBy(config: Config): CompressedBy = HttpDatabaseFetch.CompressedBy.withName(configuration.getString("geolocation.geoip2db.compression.type")) match {
      case HttpDatabaseFetch.CompressedBy.GZIP => new CompressedByGZIP(config.getBytes("geolocation.geoip2db.compression.chunkSize").toInt)
      case HttpDatabaseFetch.CompressedBy.DEFLATE => new CompressedByDEFLATE(config.getBytes("geolocation.geoip2db.compression.chunkSize").toInt)
      case HttpDatabaseFetch.CompressedBy.NONE => CompressedByNONE
  }

  @Provides
  def eTagFilter(cache: SyncCacheApi, actorSystem: ActorSystem): RememberedHeadersFilter = {
    implicit val ec: MessageDispatcher = actorSystem.dispatchers.lookup(configuration.getString("geolocation.geoip2db.download.dispatcherName"))
    new RememberedHeadersFilter(new RememberedEtagLastModifiedHeaders(new RememberedAtSyncCacheHeaders(
      cache, // Maybe it should be possible to specify headers placement from config?..
      Duration(configuration.getString("geolocation.geoip2db.download.headers.validFor"))
    )))
  }

  @Provides
  def dbFile(): File = new File(configuration.getString("geolocation.geoip2db.dbFile"))

  @Provides
  def downloadRequestProviderSettings(): DownloadRequest.Settings =
    DownloadRequest.Settings(configuration.getString("geolocation.geoip2db.dbUrl"))

  @Provides
  def downloadRequestProvider(ws: StandaloneWSClient, settings: DownloadRequest.Settings, filter: RememberedHeadersFilter): DownloadRequest =
    new DownloadRequest(ws, settings, filter)

  @Provides
  def httpDatabaseFetch(requestProvider: DownloadRequest, decompressed: CompressedBy)(implicit m: Materializer): DatabaseFetch =
    new HttpDatabaseFetch(requestProvider, decompressed)

  @Provides
  def durationToFirstWednesdayOfNextMonth(): LocalDateTime => FiniteDuration = new DurationToFirstWednesdayOfNextMonth()

  @Provides
  @Named("databaseFileProviderProps")
  def databaseFileProviderProps(fetch: DatabaseFetch, actualizeTimeout: LocalDateTime => FiniteDuration, file: File): Props =
    DatabaseFileProvider.props(fetch, actualizeTimeout, file)

  @Provides
  @Named("databaseReaderProvider")
  def databaseReaderProviderProps(): Props = {
    val locales = configuration.getStringList("play.i18n.langs")
    DatabaseReaderProvider.props(DatabaseReaderProvider.Settings(locales))
  }

  @Provides
  @Named("databaseReaderActor")
  def databaseReaderActorProps(): Props =
    if (1 == configuration.getInt("geolocation.geoip2db.instances")) DatabaseReaderActor.props() else FromConfig.props(DatabaseReaderActor.props())

  @Provides
  @Named("geoIP2DBSupervisorProps")
  def geoIP2DBSupervisorProps(
                          @Named("databaseFileProviderProps") fileProvider: Props,
                          @Named("databaseReaderProvider") readerProvider: Props,
                          @Named("databaseReaderActor") reader: Props
                        ): Props =
    GeoIP2DBSupervisor.props(fileProvider, readerProvider, reader)

  @Provides
  @Named("geoIP2DBSupervisor")
  def geoIP2DBSupervisor(@Named("geoIP2DBSupervisorProps") props: Props, system: ActorSystem): ActorRef =
    system.actorOf(props, "geolocation-supervisor")
}

private class DatabaseReaderActorProvider @Inject() (@Named("geoIP2DBSupervisor") supervisor: ActorRef) extends Provider[ActorRef] {
  override def get:ActorRef = {
    val fiveSeconds = 5.seconds
    implicit val timeout: Timeout = Timeout(fiveSeconds)
    Await.result((supervisor ? GeoIP2DBSupervisor.Reader).mapTo[ActorRef], fiveSeconds)
  }
}
