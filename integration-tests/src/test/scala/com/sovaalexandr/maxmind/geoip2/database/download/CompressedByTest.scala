package com.sovaalexandr.maxmind.geoip2.database.download

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Compression, Sink, Source}
import akka.testkit.{TestKit, TestProbe}
import akka.util.ByteString
import org.junit.Test
import org.scalatest.junit.JUnitSuiteLike

import scala.concurrent.duration._

class CompressedByTest extends TestKit(ActorSystem("compressed-test-system")) with JUnitSuiteLike {
  implicit val mat: ActorMaterializer = ActorMaterializer()

  @Test
  @throws[Exception]
  def shouldDeCompressGZIP(): Unit = {
    val decompress = new CompressedByGZIP(1024)
    val compressedSource = Source.single(ByteString("some test content")).via(Compression.gzip)

    try {
      val probe = TestProbe()
      decompress(compressedSource).map(_.utf8String).to(Sink.actorRef(probe.ref, "completed")).run()

      probe.expectMsg(1.second, "some test content")
    } catch {
      case e: AssertionError => fail(e.getMessage)
    }
  }

  @Test
  @throws[Exception]
  def shouldDeCompressDEFLATE(): Unit = {
    val decompress = new CompressedByDEFLATE(1024)
    val compressedSource = Source.single(ByteString("some test content")).via(Compression.deflate)

    try {
      val probe = TestProbe()
      decompress(compressedSource).map(_.utf8String).to(Sink.actorRef(probe.ref, "completed")).run()

      probe.expectMsg(1.second, "some test content")
    } catch {
      case e: AssertionError => fail(e.getMessage)
    }
  }

  @Test
  @throws[Exception]
  def shouldDeCompressNONE(): Unit = {
    val decompress = CompressedByNONE
    val compressedSource = Source.single(ByteString("some test content"))

    try {
      val probe = TestProbe()
      decompress(compressedSource).map(_.utf8String).to(Sink.actorRef(probe.ref, "completed")).run()

      probe.expectMsg(1.second, "some test content")
    } catch {
      case e: AssertionError => fail(e.getMessage)
    }
  }
}
