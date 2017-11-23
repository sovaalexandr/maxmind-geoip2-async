package com.sovaalexandr.maxmind.geoip2.database.download

import com.sovaalexandr.MockitoAnswer
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{MustMatchers, WordSpec}
import play.api.http.HeaderNames._
import play.api.http.{Port, Status}
import play.api.libs.ws.{StandaloneWSRequest, StandaloneWSResponse, WSRequestExecutor}
import play.api.test.WsTestClient._
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

import scala.concurrent.ExecutionContext._
import scala.concurrent.Future


class RememberedHeadersFilterTest extends WordSpec with MustMatchers with MockitoSugar with DefaultAwaitTimeout with FutureAwaits with MockitoAnswer {
  import Implicits.global

  private val portNumber = 0
  private val emptyHeaders = Map.empty[String, Seq[String]]
  private val testUrl = "/any/test/uri"
  private val testKey = s"http://localhost:$portNumber$testUrl"
  private val testETagValue = "testETagValue"
  private val responseHeadersWithETag = Map(ETAG -> Seq(testETagValue))
  private val requestHeadersWithETag = Map(IF_NONE_MATCH -> Seq(testETagValue))
  private val defaultAnswer = answer({ _ => emptyHeaders })

  private implicit val port: Port = new Port(portNumber)


  "A RememberedHeaders WSFilter" should {

    "leave request unmodified if there are no header value at cache" in withClient (client => {
      val request = client.url(testUrl)

      val mockExecutor = mock[WSRequestExecutor]
      when(mockExecutor.apply(any[StandaloneWSRequest])) thenAnswer answer { invocation => {
        invocation.getArgument[StandaloneWSRequest](0) mustEqual request  // The main check

        val response = mock[StandaloneWSResponse]
        when(response.status) thenReturn Status.OK
        when(response.headers) thenReturn emptyHeaders

        Future.successful(response)
      }}

      val filter = new RememberedHeadersFilter(mock[RememberedHeaders](defaultAnswer))
      await(filter(mockExecutor).apply(request))
    })

    "not put anything to storage if StandaloneWSResponse does not contain headers" in withClient (client => {
      val headers = mock[RememberedHeaders](defaultAnswer)

      val mockExecutor = mock[WSRequestExecutor]
      when(mockExecutor.apply(any[StandaloneWSRequest])) thenAnswer answer { _ => {

        val response = mock[StandaloneWSResponse]
        when(response.status) thenReturn Status.OK
        when(response.headers) thenReturn emptyHeaders

        Future.successful(response)
      }}

      val filter = new RememberedHeadersFilter(headers)
      await(filter(mockExecutor).apply(client.url(testUrl)))

      verify(headers, never()).set(anyString(), any()) // The main check
    })

    "add headers to StandaloneWSRequest if got any from storage" in withClient (client => {
      val headers = mock[RememberedHeaders](defaultAnswer)
      when(headers.get(testKey)) thenReturn requestHeadersWithETag // Main precondition

      val request = client.url(testUrl)

      val mockExecutor = mock[WSRequestExecutor]
      when(mockExecutor.apply(any[StandaloneWSRequest])) thenAnswer answer { invocation => {
        invocation.getArgument[StandaloneWSRequest](0).header(IF_NONE_MATCH) must contain(testETagValue) // The main check

        val response = mock[StandaloneWSResponse]
        when(response.status) thenReturn Status.OK
        when(response.headers) thenReturn emptyHeaders

        Future.successful(response)
      }}

      val filter = new RememberedHeadersFilter(headers)
      await(filter(mockExecutor).apply(request))
    })

    "store headers if any header is present at StandaloneWSResponse" in withClient (client => {
      val headers = mock[RememberedHeaders]
      when(headers.get(testKey)) thenReturn emptyHeaders

      val mockExecutor = mock[WSRequestExecutor]
      when(mockExecutor.apply(any[StandaloneWSRequest])) thenAnswer answer { _ => {

        val response = mock[StandaloneWSResponse]
        when(response.status) thenReturn Status.OK
        when(response.headers) thenReturn responseHeadersWithETag // Main precondition

        Future.successful(response)
      }}

      val filter = new RememberedHeadersFilter(headers)
      await(filter(mockExecutor).apply(client.url(testUrl)))

      // Cheating with timeouts because of usage of onComplete callback on completed Future. Await only or single threaded executor just don't work.
      verify(headers, timeout(100).times(1)).set(testKey, responseHeadersWithETag) // The main check
    })

    "not store headers if StandaloneWSResponse wasn't Ok" in withClient (client => {
      val headers = mock[RememberedHeaders](defaultAnswer)

      val mockExecutor = mock[WSRequestExecutor]
      when(mockExecutor.apply(any[StandaloneWSRequest])) thenAnswer answer { _ => {

        val response = mock[StandaloneWSResponse]
        when(response.status) thenReturn Status.NOT_MODIFIED
        when(response.headers) thenReturn responseHeadersWithETag // Main precondition

        Future.successful(response)
      }}

      val filter = new RememberedHeadersFilter(headers)
      await(filter(mockExecutor).apply(client.url(testUrl)))

      verify(headers, never()).set(anyString(), any()) // The main check
    })

    "not store headers if interaction wasn't completed successfully" in withClient (client => {
      val headers = mock[RememberedHeaders]
      when(headers.get(testKey)) thenReturn emptyHeaders

      val mockExecutor = mock[WSRequestExecutor]
      when(mockExecutor.apply(any[StandaloneWSRequest])) thenAnswer answer { _ => {
        Future.failed(new RuntimeException) // Main precondition
      }}

      val filter = new RememberedHeadersFilter(headers)
      intercept[RuntimeException] {await(filter(mockExecutor).apply(client.url(testUrl)))}

      verify(headers, never()).set(anyString(), any()) // The main check
    })
  }
}