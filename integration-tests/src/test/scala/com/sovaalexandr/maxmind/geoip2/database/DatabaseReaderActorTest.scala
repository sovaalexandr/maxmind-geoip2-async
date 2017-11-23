package com.sovaalexandr.maxmind.geoip2.database

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit, TestProbe}
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.AddressNotFoundException
import com.maxmind.geoip2.model.{CityResponse, CountryResponse}
import com.sovaalexandr.maxmind.geoip2.{AddressNotFound, City, Country, Idle}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.mockito.MockitoSugar
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

class DatabaseReaderActorTest extends Sequential(
  new DatabaseReaderActorStartsTest(ActorSystem("DatabaseReaderActorStartsTest", AkkaSettings.config)),
  new DatabaseReaderActorActualizedTest(ActorSystem("DatabaseReaderActorActualizedTest", AkkaSettings.config))
) {
}

class DatabaseReaderActorStartsTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with MustMatchers with BeforeAndAfterAll with MockitoSugar with DefaultAwaitTimeout with FutureAwaits
{
  import DatabaseReaderActor._

  "DatabaseReaderActor" when {

    val probe = TestProbe("subscriber")
    system.eventStream.subscribe(probe.ref, classOf[DatabaseReaderConsumerAdded])

    val target = TestFSMRef(new DatabaseReaderActor)

    "starts" should {

      "be at GotNoReader state" in {
        target.stateName must be(GotNoReader)
        target.stateData must be(WithoutReader)
      }

      "fire event about DatabaseReader consumer added" in {
        probe.expectMsgClass(classOf[DatabaseReaderConsumerAdded])
      }

      "respond Idle to City request" in {
        await(target ? City(mock[InetAddress])) must be (Idle)
      }
      "respond Idle to Country request" in {
        await(target ? Country(mock[InetAddress])) must be (Idle)
      }
    }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}

class DatabaseReaderActorActualizedTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with MustMatchers with BeforeAndAfterAll with MockitoSugar with DefaultAwaitTimeout with FutureAwaits
{
  import DatabaseReaderActor._

  "DatabaseReaderActor" when {

    val probe = TestProbe("subscriber")
    system.eventStream.subscribe(probe.ref, classOf[DatabaseReaderConsumerAdded])

    val target = TestFSMRef(new DatabaseReaderActor)

    "received DatabaseActualized message" should {

      val reader = mock[DatabaseReader]
      val readerActualized = DatabaseReaderActualized(reader)
      system.eventStream.publish(readerActualized)

      "change state to WithReader" in {
        target.stateName must be (WithReader)
      }

      val ipAddress = mock[InetAddress]
      "be able to reply to Country requests" in {
        val countryResponse = new CountryResponse(null, null, null, null, null, null) // Mockito can't mock final classes
        when(reader.country(any[InetAddress])) thenReturn countryResponse

        await(target ? Country(ipAddress)) must be (countryResponse)
      }
      "be able to reply to City requests" in {
        val cityResponse = new CityResponse(null, null, null, null, null, null, null, null, null, null) // Mockito can't mock final classes
        when(reader.city(any[InetAddress])) thenReturn cityResponse

        await(target ? City(ipAddress)) must be (cityResponse)
      }
      "be able to reply to addressNotFound" in {
        when(reader.city(any[InetAddress])) thenThrow new AddressNotFoundException("any test message")
        await(target ? City(ipAddress)) must be (AddressNotFound)
      }
    }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}
