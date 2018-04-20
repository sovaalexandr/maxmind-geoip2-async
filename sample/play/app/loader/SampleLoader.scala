package loader

import akka.actor.ActorRef
import com.sovaalexandr.StandaloneAhcWSComponents
import com.sovaalexandr.maxmind.geoip2.GeoIP2Components
import com.typesafe.config.Config
import controllers.{GeoJava, GeoScala}
import play.api.cache.SyncCacheApi
import play.api.cache.ehcache.EhCacheComponents
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext}
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient
import router.Routes

class SampleLoader extends play.api.ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application =
    new SampleComponents(context).application
}

class SampleComponents(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context)
    with EhCacheComponents
    with GeoIP2Components
    with AhcWSComponents
    with StandaloneAhcWSComponents {
  override def httpFilters: Seq[EssentialFilter] = Seq.empty

  override protected val config: Config = configuration.underlying
  override protected val ws: StandaloneWSClient = scalaAhcWSClient()
  override protected val cache: SyncCacheApi = defaultCacheApi.sync
  override protected val asyncHttpClient: AsyncHttpClient = wsClient.underlying

  private val ref: ActorRef = databaseReaderRef()
  private val geo = new views.html.Geo
  private val error = new views.html.Error

  lazy val router: Router = new Routes(httpErrorHandler,
    new controllers.Application(new views.html.IndexTemplate()),
    new GeoJava(ref, geo, error),
    new GeoScala(controllerComponents, ref, geo, error)
  )
}
