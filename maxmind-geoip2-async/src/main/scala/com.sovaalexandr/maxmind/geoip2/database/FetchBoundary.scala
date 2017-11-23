package com.sovaalexandr.maxmind.geoip2.database

import java.io.File

import scala.concurrent.Future

/**
 * Boundary between DataBaseReaderProvider and DatabaseFetch
 */


trait CurrentDatabaseFile {
  def stillFine(): DatabaseFileEvent
  def gotNewOne(file: File): DatabaseFileEvent
}

trait DatabaseFileEvent

object DatabaseFile {
  private case object StillFine extends DatabaseFile {
    override def apply(current: CurrentDatabaseFile): DatabaseFileEvent = current.stillFine()
  }
  private case class GotNewOne(file: File) extends DatabaseFile {
    override def apply(current: CurrentDatabaseFile): DatabaseFileEvent = current.gotNewOne(file)
  }

  def apply(): DatabaseFile = StillFine
  def apply(file: File): DatabaseFile = GotNewOne(file)
}
sealed abstract class DatabaseFile extends (CurrentDatabaseFile => DatabaseFileEvent)

/**
 * Service to fetch a DBFile
 */
trait DatabaseFetch {
  def fetchNow(dbFile: File): Future[DatabaseFile]
}
