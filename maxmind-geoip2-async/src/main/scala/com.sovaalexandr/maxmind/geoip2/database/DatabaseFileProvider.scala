package com.sovaalexandr.maxmind.geoip2.database

import java.io.File
import java.time.LocalDateTime
import java.time.LocalDateTime.{MIN, now}

import akka.actor.Props
import akka.pattern.pipe
import akka.persistence.fsm.PersistentFSM

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.reflect.ClassTag

object DatabaseFileProvider {
  def props(fetch: DatabaseFetch, actualizeTimeout: LocalDateTime => FiniteDuration, initialFile: File) =
    Props(new DatabaseFileProvider(fetch, actualizeTimeout)(initialFile))

  private[database] trait DatabaseFileState extends PersistentFSM.FSMState
  private[database] case object Outdated extends DatabaseFileState {
    override def identifier: String = "Outdated"
  }
  private[database] case object Actualizing extends DatabaseFileState {
    override def identifier: String = "Actualizing"
  }
  private[database] case object Ready extends DatabaseFileState {
    override def identifier: String = "Ready"
  }

  // Commands in addition to defined at FileBoundary.scala
  private[database] case object Actualize

  // Events in addition to defined at FileBoundary.scala
  private[database] case class Actualized(file: File) extends DatabaseFileEvent
  private[database] case object StillFine extends DatabaseFileEvent

  // State data
  private[database] case class CurrentFile(file: File, updatedAt: LocalDateTime = now) extends CurrentDatabaseFile {
    override def stillFine(): DatabaseFileEvent = StillFine
    override def gotNewOne(file: File): DatabaseFileEvent = Actualized(file)
  }
}

/**
 * @param fetch - service to fetch a DBFile from outer space like web.
 * @param actualizeTimeout - service to calculate how much time should pass till next DBFile update.
 * @param initialFile - initial place at filesystem where potential DBFile is. Location is also should be write-able.
 */
class DatabaseFileProvider(fetch: DatabaseFetch, actualizeTimeout: LocalDateTime => FiniteDuration)(initialFile: File) extends PersistentFSM[DatabaseFileProvider.DatabaseFileState, DatabaseFileProvider.CurrentFile, DatabaseFileEvent] {
  override def persistenceId: String = "maxmind.geoip2db.database"
  import DatabaseFileProvider._
  private implicit val ec: ExecutionContextExecutor = context.dispatcher

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[FileConsumerAdded])
    context.system.eventStream.subscribe(self, classOf[NoMoreAFileConsumer])
    super.preStart()
  }

  startWith(Ready, CurrentFile(initialFile, MIN))

  when(Outdated) {
    case Event(Actualize, _) =>
      goto(Actualizing) andThen { stateData => pipe(fetch.fetchNow(stateData.file).map(_(stateData))) to self }
  }

  when(Actualizing) { // Maybe timeout suits here. It would be like ask timeout indicating for how long should update happen.
    case Event(event @Actualized(_), _) => goto(Ready) applying event andThen { _ => saveStateSnapshot() }
    case Event(event @StillFine, _) => goto(Ready) applying event
  }

  when(Ready) {
    case Event(event @Actualize, _) => goto(Outdated) andThen { _ => self ! event}
    case Event(FileConsumerAdded(at), currentFile) => at ! DatabaseActualized(currentFile.file)
      stay
  }

  override def applyEvent(domainEvent: DatabaseFileEvent, currentFile: CurrentFile): CurrentFile = domainEvent match {
    case Actualized(file) =>
      context.system.eventStream.publish(DatabaseActualized(file)) // gossip
      context.system.scheduler.scheduleOnce(actualizeTimeout(currentFile.updatedAt), self, Actualize)
      CurrentFile(file)
    case StillFine =>
      context.system.scheduler.scheduleOnce(actualizeTimeout(currentFile.updatedAt), self, Actualize)
      currentFile
  }

  override def onRecoveryCompleted(): Unit = {
    context.system.scheduler.scheduleOnce(actualizeTimeout(stateData.updatedAt), self, Actualize)
    // TODO: fire only when really actualized. New file consummers should get it only on FileConsumerAdded event.
    context.system.eventStream.publish(DatabaseActualized(stateData.file))
  }

  override implicit def domainEventClassTag: ClassTag[DatabaseFileEvent] = ClassTag(classOf[DatabaseFileEvent])
}
