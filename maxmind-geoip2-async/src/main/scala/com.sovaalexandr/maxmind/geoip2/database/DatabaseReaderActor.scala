package com.sovaalexandr.maxmind.geoip2.database

import java.net.InetAddress

import akka.actor.{FSM, Props}
import com.sovaalexandr.maxmind.geoip2.{City, Country, Idle, AddressNotFound}
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.AddressNotFoundException

import scala.util.{Failure, Success, Try}

object DatabaseReaderActor {
  def props(): Props = Props(new DatabaseReaderActor)

  sealed trait State
  case object GotNoReader extends State
  case object WithReader extends State

  sealed trait StateData
  case object WithoutReader extends StateData
  case class WithReaderOf(reader: DatabaseReader) extends StateData
}

class DatabaseReaderActor extends FSM[DatabaseReaderActor.State, DatabaseReaderActor.StateData] {
  import DatabaseReaderActor._

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[DatabaseReaderActualized])
    context.system.eventStream.publish(DatabaseReaderConsumerAdded(self))
  }

  startWith(GotNoReader, WithoutReader)

  when(GotNoReader) {
    case Event(Country(_), _) => stay replying Idle
    case Event(City(_), _) => stay replying Idle
    // Duplicate to avoid java.util.NoSuchElementException
    case Event(DatabaseReaderActualized(reader), _) => goto(WithReader) using WithReaderOf(reader)
  // maybe here should be re-routing of GeoIp2Request if any happen at GotNoReader State
  }

  when(WithReader) {
    case Event(Country(ofIp), WithReaderOf(reader)) => stay replying locate(reader.country(ofIp))
    case Event(City(ofIp), WithReaderOf(reader)) => stay replying locate(reader.city(ofIp))
    // Reader could be actualized at any state.
    case Event(DatabaseReaderActualized(reader), _) => stay using WithReaderOf(reader)
  }

  private def locate[T](l: => T) = Try(l) match {
    case Success(location) => location
    case Failure(_: AddressNotFoundException) => AddressNotFound
    case Failure(exception) => throw exception // Supervision should know about it
  }

  initialize()

  override def postStop(): Unit = context.system.eventStream.publish(NoMoreADatabaseReaderConsumer(self))
}
