#MaxMind GeoIP2 Java API Database plugin

[![Build Status](https://travis-ci.org/sovaalexandr/maxmind-geoip2-async.svg)](https://travis-ci.org/sovaalexandr/maxmind-geoip2-async)

This is a plugin to use [MaxMind GeoIP2 Database](https://github.com/maxmind/GeoIP2-java#database-usage) to find out about geolocation of some particular IP address without constant calls to remote web services.
This plugin supports:

 - Async concurrent interactions with DatabaseReader under the hood
 - Scheduled renewals of MaxMind database
 - Handles ETag and Last-Modified headers at cache to minimize impact onto outer service (where DB file is stored).

Plugin is built on top of [Akka](https://akka.io/) and is a set of actors where you can send message to ad get a response.
It uses [Akka Streams](https://doc.akka.io/docs/akka/current/scala/stream/index.html) and [Play! standalone ws](https://github.com/playframework/play-ws) to download and process DB file.

## Installation

The first step is include the the dependency in your `build.sbt` file:

### Case of [Guice](https://github.com/google/guice) DI and [Play!](https://www.playframework.com/) frameworks

```scala
scalaVersion := "2.12.4"

libraryDependencies += "com.sovaalexandr" %% "maxmind-geoip2-async-guice" % "1.0.0-SNAPSHOT"
```

Module will be added automatically.

## Configuration

This plugins offers the following configurations:
```
geolocation {
  geoip2db {
    instances = ${akka.actor.deployment."/geolocation-supervisor/geolocation".nr-of-instances} // How much IPs do you want to locate at a time
    dbUrl = "http://geolite.maxmind.com/download/geoip/database/GeoLite2-City.mmdb.gz" // URL where to go for new DB file.
    dbFile = ${java.io.tmpdir}"/GeoLite2-City.mmdb" // Place where to store DB Files. Directory must be writeable.
    download {
      dispatcherName = akka.actor.default-dispatcher // Dispatcher which will execute all actions related to file download.
      headers {
        validFor: Inf // Plugin also stores ETag and Last-Modified headers at cache. This setting is FiniteDuration of that validity
      }
    }
    compression {
      type = GZIP // Usually GeoLite DB files are GZip-compressed. This setting'll decompress a file downstream.
      // Accepted options: GZIP, DEFLATE, NONE
      chunkSize = 64kB //${akka.stream.scaladsl.Compression.MaxBytesPerChunkDefault} - size of chunk to decompress. Set to AkkaStreams default.
    }
  }
}
```
More in detail here: [reference.conf](https://github.com/sovaalexandr/maxmind-geoip2-async/blob/master/maxmind-geoip2-async/src/main/resources/reference.conf)

## Code example:

You can see the full Playframework integration example [at samples dir](https://github.com/sovaalexandr/maxmind-geoip2-async/blob/master/sample/play).
This is an example of Play **Scala** controller injected with Guice:

```scala
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
```

And similar example of Play **Java** controller injected with Guice:

```java
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
            return toJava(ask(geolocation, City.apply(InetAddress.getByName(address)), 5000L))
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
        } if (response instanceof AddressNotFound) {
            return notFound(errorTemplate.render("MaxMind don't know where is it"));
        } if (response instanceof Idle) {
            return internalServerError(errorTemplate.render("Geolocation is warming-up. Maybe after restart or there is a file renewal. Try a bit later."));
        }

        return internalServerError(errorTemplate.render("Something wierd have returned: "+response.getClass().toString()));
    }
}
```

Feedback, suggestions, bug reports, code quality reviews and contributions are extremely appreciated.
See details at [CONTRIBUTING](https://github.com/sovaalexandr/maxmind-geoip2-async/blob/master/CONTRIBUTING.md).
