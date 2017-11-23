package com.sovaalexandr.maxmind.geoip2.database

import java.io.File

import akka.actor.ActorRef

case class FileConsumerAdded(at: ActorRef)

case class DatabaseActualized(file: File)

case class NoMoreAFileConsumer(at: ActorRef)
