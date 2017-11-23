package com.sovaalexandr.maxmind.geoip2.database

import akka.actor.ActorRef
import com.maxmind.geoip2.DatabaseReader

case class DatabaseReaderConsumerAdded(at: ActorRef)

case class NoMoreADatabaseReaderConsumer(at: ActorRef)

case class DatabaseReaderActualized(reader: DatabaseReader)
