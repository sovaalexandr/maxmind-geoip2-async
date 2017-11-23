package com.sovaalexandr.maxmind.geoip2.database

import java.io.File
import java.{util => ju}

import akka.actor.{FSM, Props}
import com.maxmind.db.Reader.FileMode.MEMORY
import com.maxmind.db.{CHMCache, NodeCache}
import com.maxmind.geoip2.DatabaseReader

object DatabaseReaderProvider {

  // Maybe fileMode: FileMode should be added but it will harm ability to resolve a corrupted file problem.
  case class Settings(locales: ju.List[String], cache: NodeCache = new CHMCache())// import collection.JavaConverters.seqAsJavaList

  def props(settings: Settings) = Props(new DatabaseReaderProvider(settings))

  // Yes, those are 1:1 copy of DatabaseReaderActor states but we can't use DatabaseReaderActor states because it would
  // be a "tight coupling" which should be avoided.
  sealed trait State
  case object GotNoReader extends State
  case object WithReader extends State

  sealed trait StateData
  case object WithoutReader extends StateData
  case class WithReaderOf(reader: DatabaseReader) extends StateData
}

class DatabaseReaderProvider(settings: DatabaseReaderProvider.Settings) extends FSM[DatabaseReaderProvider.State, DatabaseReaderProvider.StateData] {
  import DatabaseReaderProvider._

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[DatabaseActualized])
    context.system.eventStream.subscribe(self, classOf[DatabaseReaderConsumerAdded])
    context.system.eventStream.subscribe(self, classOf[NoMoreADatabaseReaderConsumer])
    context.system.eventStream.publish(FileConsumerAdded(self))
  }

  startWith(GotNoReader, WithoutReader)

  when(GotNoReader) {
    case Event(DatabaseActualized(file), _) => goto(WithReader) using withReaderOf(file)
  }

  when(WithReader) {
    case Event(DatabaseReaderConsumerAdded(at), WithReaderOf(reader)) => at ! DatabaseReaderActualized(reader)
      stay
    case Event(DatabaseActualized(file), _) => stay using withReaderOf(file)
  }

  initialize()

  override def postStop(): Unit = context.system.eventStream.publish(NoMoreAFileConsumer(self))

  // Side effect for DRY applied as it's used at two places
  private def withReaderOf(file: File): WithReaderOf = {
    val reader = new DatabaseReader.Builder(file).fileMode(MEMORY).withCache(settings.cache).locales(settings.locales).build()
    // Yes, according to documentation https://github.com/maxmind/GeoIP2-java#multi-threaded-use we can share
    // constructed instance across threads within single JVM.
    context.system.eventStream.publish(DatabaseReaderActualized(reader)) // gossip

    WithReaderOf(reader)
  }
}
