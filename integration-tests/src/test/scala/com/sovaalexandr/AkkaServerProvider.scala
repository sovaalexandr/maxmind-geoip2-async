package com.sovaalexandr

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, Future}

// Complete copy of play.AkkaServerProvider adopted for Scalatest.
trait AkkaServerProvider extends BeforeAndAfterAll { this: Suite =>

  /**
    * @return Routes to be used by the test.
    */
  def routes: Route

  var testServerPort: Int = _
  val defaultTimeout: FiniteDuration = 5.seconds

  // Create Akka system for thread and streaming management
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  lazy val futureServer: Future[Http.ServerBinding] = {
    // Using 0 (zero) means that a random free port will be used.
    // So our tests can run in parallel and won't mess with each other.
    Http().bindAndHandle(routes, "localhost", 0)
  }

  override def beforeAll(): Unit = {
    val portFuture = futureServer.map(_.localAddress.getPort)(system.dispatcher)
    portFuture.foreach { port => testServerPort = port }(system.dispatcher)
    Await.ready(portFuture, defaultTimeout)
  }

  override def afterAll(): Unit = {
    futureServer.foreach(_.unbind())(system.dispatcher)
    val terminate = system.terminate()
    Await.ready(terminate, defaultTimeout)
  }
}
