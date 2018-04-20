package com.sovaalexandr

import akka.stream.Materializer
import play.api.libs.ws.StandaloneWSClient
import play.libs.ws.ahc.StandaloneAhcWSClient
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient

trait StandaloneAhcWSComponents {
  protected val asyncHttpClient: AsyncHttpClient
  implicit protected val materializer: Materializer

  private lazy val javaClient = new StandaloneAhcWSClient(asyncHttpClient, materializer)
  def javaAhcWSClient(): StandaloneAhcWSClient = javaClient

  private lazy val scalaClient = new play.api.libs.ws.ahc.StandaloneAhcWSClient(asyncHttpClient)
  def scalaAhcWSClient(): StandaloneWSClient = scalaClient
}
