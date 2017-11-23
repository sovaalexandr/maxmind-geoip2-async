package com.sovaalexandr.maxmind.geoip2.database

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit, TestProbe}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, MustMatchers, Sequential, WordSpecLike}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

class DatabaseReaderProviderTest extends Sequential(
  new DatabaseReaderProviderStartsTest(ActorSystem("DatabaseReaderProviderStartsTest", AkkaSettings.config)),
  new DatabaseReaderActorActualizedTest(ActorSystem("DatabaseReaderProviderActualizedTest", AkkaSettings.config))
)

class DatabaseReaderProviderStartsTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with MustMatchers with BeforeAndAfterAll with MockitoSugar with DefaultAwaitTimeout with FutureAwaits
{
  import DatabaseReaderProvider._

  "A DatabaseReaderProvider" when {
    val probe = TestProbe("subscriber")
    system.eventStream.subscribe(probe.ref, classOf[FileConsumerAdded])
    system.eventStream.subscribe(probe.ref, classOf[DatabaseReaderActualized])

    import collection.JavaConverters.seqAsJavaList
    val settings = Settings(seqAsJavaList(Seq("en")))
    val target = TestFSMRef(new DatabaseReaderProvider(settings))

    "starts" should {

      "be in GotNoReader, WithoutReader state" in {
        target.stateName must be(GotNoReader)
        target.stateData must be(WithoutReader)
      }

      "fire event about Database File consumer added" in {
        probe.expectMsgClass(classOf[FileConsumerAdded])
      }
    }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}

class DatabaseReaderProviderActualizedTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with MustMatchers with BeforeAndAfterAll with MockitoSugar with DefaultAwaitTimeout with FutureAwaits
{
  import DatabaseReaderProvider._

  "A DatabaseReaderProvider" when {
    val probe = TestProbe("subscriber")
    system.eventStream.subscribe(probe.ref, classOf[FileConsumerAdded])
    system.eventStream.subscribe(probe.ref, classOf[DatabaseReaderActualized])

    import collection.JavaConverters.seqAsJavaList
    val settings = Settings(seqAsJavaList(Seq("en")))
    val target = TestFSMRef(new DatabaseReaderProvider(settings))

    "received DatabaseActualized message" should {

      val file = mock[File]
      val fileActualized = DatabaseActualized(file)
      system.eventStream.publish(fileActualized)

      "change state to WithReader" in {
        target.stateName must be (WithReader)
        probe.expectMsgClass(classOf[DatabaseReaderActualized])
      }
    }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}
