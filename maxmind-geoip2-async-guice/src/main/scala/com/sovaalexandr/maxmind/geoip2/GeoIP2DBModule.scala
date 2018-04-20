package com.sovaalexandr.maxmind.geoip2

import javax.inject.Inject

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, Provider}
import com.typesafe.config.Config
import play.Environment
import play.api.cache.SyncCacheApi
import play.api.libs.ws.StandaloneWSClient

class GeoIP2DBModule(environment: Environment, configuration: Config) extends AbstractModule {
  override def configure(): Unit = bind(classOf[ActorRef])
    .annotatedWith(Names.named("geolocation"))
    .toProvider(classOf[DatabaseReaderActorProvider]).asEagerSingleton()
}

private class DatabaseReaderActorProvider @Inject() (
                                                      override protected val actorSystem: ActorSystem,
                                                      override protected val config: Config,
                                                      override protected val ws: StandaloneWSClient,
                                                      override protected val cache: SyncCacheApi
                                                    )(implicit m: Materializer)
  extends Provider[ActorRef] with GeoIP2Components {
  override def get:ActorRef = databaseReaderRef()
}
