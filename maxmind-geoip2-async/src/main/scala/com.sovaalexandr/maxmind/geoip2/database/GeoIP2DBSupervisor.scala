package com.sovaalexandr.maxmind.geoip2.database

import java.io.FileNotFoundException

import akka.actor.SupervisorStrategy.{Escalate, Resume}
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import com.maxmind.geoip2.exception.GeoIp2Exception

object GeoIP2DBSupervisor {
  def props(fileProvider: Props, readerProvider: Props, reader: Props) = Props(
    new GeoIP2DBSupervisor(fileProvider, readerProvider, reader)
  )
  case object Reader
}

class GeoIP2DBSupervisor(fileProvider: Props, readerProvider: Props, reader: Props) extends Actor {
  var readerRef:ActorRef = _

  override def supervisorStrategy: OneForOneStrategy = OneForOneStrategy() {
    case _: NullPointerException => Resume
    case _: FileNotFoundException => Resume
    case _: GeoIp2Exception => Resume
    case _: Exception => Escalate
  }

  override def preStart(): Unit = {
    context.actorOf(fileProvider, "database-file-provider")
    context.actorOf(readerProvider, "database-reader-provider")
    readerRef = context.actorOf(reader, "geolocation")
  }

  override def receive: Receive = {
    case GeoIP2DBSupervisor.Reader => sender() ! readerRef
  }
}
