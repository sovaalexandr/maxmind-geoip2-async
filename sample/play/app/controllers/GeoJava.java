package controllers;

import akka.actor.ActorRef;
import com.maxmind.geoip2.model.CityResponse;
import com.sovaalexandr.maxmind.geoip2.AddressNotFound$;
import com.sovaalexandr.maxmind.geoip2.City;
import com.sovaalexandr.maxmind.geoip2.Idle$;
import play.mvc.Controller;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static akka.pattern.PatternsCS.ask;

public class GeoJava extends Controller
{
    private final ActorRef geolocation;
    private final views.html.Geo geoTemplate;
    private final views.html.Error errorTemplate;

    @Inject
    public GeoJava(@Named("geolocation") ActorRef geolocation, views.html.Geo geoTemplate, views.html.Error errorTemplate)
    {
        this.geolocation = geolocation;
        this.geoTemplate = geoTemplate;
        this.errorTemplate = errorTemplate;
    }

    public CompletionStage<Result> getGeolocation(String address) {
        try {
            return ask(geolocation, City.apply(InetAddress.getByName(address)), 5000L)
                    .thenApply(location -> toResult(location, address))
                    .exceptionally(e -> badRequest(errorTemplate.render(e.getMessage())));
        } catch (UnknownHostException e) {
            return CompletableFuture.completedFuture(badRequest(errorTemplate.render(e.getMessage())));
        }
    }

    // ask returns Future[Object] so use this workaround to restrict a return type.
    private Result toResult(Object response, String address) {
        if (response instanceof CityResponse) {
            return ok(geoTemplate.render(address, (CityResponse)response, "Got this one:"));
        } if (response instanceof AddressNotFound$) {
            return notFound(errorTemplate.render("MaxMind don't know where is it"));
        } if (response instanceof Idle$) {
            return internalServerError(errorTemplate.render("Geolocation is warming-up. Maybe after restart or there is a file renewal. Try a bit later."));
        }

        return internalServerError(errorTemplate.render("Something wierd have returned: "+response.getClass().toString()));
    }
}
