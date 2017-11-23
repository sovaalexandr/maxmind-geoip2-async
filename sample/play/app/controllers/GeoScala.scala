package controllers

import java.net.InetAddress
import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.dispatch.Futures
import akka.pattern.ask
import akka.util.Timeout
import com.maxmind.geoip2.model.CityResponse
import com.sovaalexandr.maxmind.geoip2.{AddressNotFound, City, Idle}
import play.api.mvc._
import views.html.{Error, Geo}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class GeoScala @Inject()(cc: ControllerComponents, @Named("geolocation") geolocation: ActorRef, geoTemplate: Geo, errorTemplate: Error)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  def getGeolocation(address: String): Action[AnyContent] = Action.async {
    implicit val timeout: Timeout = Timeout(5.seconds)
    Try(InetAddress.getByName(address)) match {
      case Success(inet) => (geolocation ? City(inet)).map({
        case r: CityResponse => Ok(geoTemplate(address, r, "Got this one:"))
        case AddressNotFound => NotFound(errorTemplate.render("MaxMind don't know where is it"))
        case Idle => PreconditionFailed(errorTemplate.render("Geolocation is warming-up. Maybe after restart or there is a file renewal. Try a bit later."))
      }).recover({case e:Throwable => BadRequest(errorTemplate.render(e.getMessage))})
      case Failure(e) => Futures.successful(BadRequest(errorTemplate.render(e.getMessage)))
    }
  }
}
