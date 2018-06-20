package com.sovaalexandr.maxmind.geoip2.database

import java.io.File
import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.dispatch.Futures
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, MustMatchers, Sequential, WordSpecLike}

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContextExecutor, Future}

class DatabaseFileProviderTest extends Sequential(
  new DatabaseFileProviderDoNotNeedActualizationTest(ActorSystem("DatabaseFileProviderDoNotNeedActualizationTest", AkkaSettings.config))//,
//  new DatabaseFileProviderNeedsActualizationTest(ActorSystem("DatabaseFileProviderNeedsActualizationTest", AkkaSettings.config))
)

class DatabaseFileProviderDoNotNeedActualizationTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with MustMatchers with BeforeAndAfterAll with MockitoSugar
{
  private val differentDurationAnswer = new Answer[FiniteDuration] {
    private var times = 0
    override def answer(invocation: InvocationOnMock): FiniteDuration = if (0 == times) {
      times = times + 1
      2.seconds
    } else 10.seconds
  }

  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  // Both should be Serializeable while Mockito mocks are not Serializeable.
  private val existingTestFile = new File(s"${System.getProperty("java.io.tmpdir")}/DatabaseFileProviderTest/GeoLite2-City.mmdb")
  private val existingChangedFile = new File(s"${System.getProperty("java.io.tmpdir")}/DatabaseFileProviderTest/GeoLite2-City.mmdb.someTestPrefix")
  private val settings = DatabaseFileProvider.Settings("maxmind.geoip2db.database")

  "A DatabaseFileProvider" when {
    "do not need actualization" should {

      val fetch = mock[DatabaseFetch]
      when(fetch.fetchNow(existingTestFile)) thenReturn Futures.successful(DatabaseFile())

      val actualizeAfter = mock[LocalDateTime => FiniteDuration]
      when(actualizeAfter.apply(any(classOf[LocalDateTime]))) thenAnswer differentDurationAnswer

      val probe = TestProbe("subscriber")
      system.eventStream.subscribe(probe.ref, classOf[DatabaseActualized])
      system.actorOf(DatabaseFileProvider.props(fetch, actualizeAfter, existingTestFile, settings))

      "send NewFile notification when started" in {
        probe.expectMsgClass(classOf[DatabaseActualized])
      }
      "provide the same file as it have started with" in {
        val message = FileConsumerAdded(probe.ref)
        system.eventStream.publish(message)
        probe.expectMsgClass(classOf[DatabaseActualized]).file must be(existingTestFile)
      }
    }
  }

  override protected def beforeAll(): Unit = {
    new File(s"${System.getProperty("java.io.tmpdir")}/DatabaseFileProviderTest/snapshots").mkdirs()
    existingTestFile.createNewFile()
    existingChangedFile.createNewFile()
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
    Option(new File(s"${System.getProperty("java.io.tmpdir")}/DatabaseFileProviderTest/snapshots").listFiles()).foreach(_.foreach(_.delete))
  }
}

class DatabaseFileProviderNeedsActualizationTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with MustMatchers with BeforeAndAfterAll with MockitoSugar
{
  private val differentDurationAnswer = new Answer[FiniteDuration] {
    private var times = 0
    override def answer(invocation: InvocationOnMock): FiniteDuration = if (0 == times) {
      times = times + 1
      2.seconds
    } else 10.seconds
  }

  private implicit val ec = system.dispatcher

  // Both should be Serializeable while Mockito mocks are not Serializeable.
  private val existingTestFile = new File(s"${System.getProperty("java.io.tmpdir")}/DatabaseFileProviderTest/GeoLite2-City.mmdb")
  private val existingChangedFile = new File(s"${System.getProperty("java.io.tmpdir")}/DatabaseFileProviderTest/GeoLite2-City.mmdb.someTestPrefix")
  private val settings = DatabaseFileProvider.Settings("maxmind.geoip2db.database")

  "A DatabaseFileProvider" when {
    "needs actualization" should {
      val fetch = mock[DatabaseFetch]
      when(fetch.fetchNow(existingTestFile)) thenReturn Future.successful(DatabaseFile(existingChangedFile))

      val actualizeAfter = mock[LocalDateTime => FiniteDuration]
      when(actualizeAfter.apply(any(classOf[LocalDateTime]))) thenAnswer differentDurationAnswer

      val probe = TestProbe("subscriber")
      system.eventStream.subscribe(probe.ref, classOf[DatabaseActualized])
      val actorRef = system.actorOf(DatabaseFileProvider.props(fetch, actualizeAfter, existingTestFile, settings), "actor-under-test")

      "provide different file after DatabaseActualized notification" in {
        system.eventStream.publish(FileConsumerAdded(probe.ref))
//        probe.expectMsgClass(classOf[DatabaseActualized]).file must be(existingTestFile)

        probe.expectMsgClass(classOf[DatabaseActualized]).file must be(existingChangedFile)
      }
    }
  }

  override protected def beforeAll(): Unit = {
    new File(s"${System.getProperty("java.io.tmpdir")}/DatabaseFileProviderTest/snapshots").mkdirs()
    existingTestFile.createNewFile()
    existingChangedFile.createNewFile()
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
    Option(new File(s"${System.getProperty("java.io.tmpdir")}/DatabaseFileProviderTest/snapshots").listFiles()).foreach(_.foreach(_.delete))
  }
}
