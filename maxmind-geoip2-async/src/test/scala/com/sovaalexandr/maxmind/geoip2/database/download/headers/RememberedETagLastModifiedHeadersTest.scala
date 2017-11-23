package com.sovaalexandr.maxmind.geoip2.database.download.headers

import com.sovaalexandr.MockitoAnswer
import com.sovaalexandr.maxmind.geoip2.database.download.RememberedHeaders
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{MustMatchers, WordSpec}
import play.mvc.Http.HeaderNames._

class RememberedETagLastModifiedHeadersTest extends WordSpec with MustMatchers with MockitoSugar with MockitoAnswer {

  private val testKey = "someTestKey"
  private val emptyReply = Map.empty[String, Seq[String]]

  "ETagLastModifiedHeaders" should {

    s"not put anything further if candidate value does not contain neither $ETAG nor $LAST_MODIFIED values" in {
      val headers = mock[RememberedHeaders]
      val testValue = Map("foo" -> Seq("bar"))
      val target = new RememberedEtagLastModifiedHeaders(headers)

      target.set(testKey, testValue)

      verify(headers, never()).set(anyString(), any())
    }

    s"get empty reply if headers not contain neither $ETAG nor $LAST_MODIFIED values" in {
      val headers = mock[RememberedHeaders](answer({ _ => emptyReply }))
      val testValue = Map("foo" -> Seq("bar"))
      when(headers.get(testKey)) thenReturn testValue

      val target = new RememberedEtagLastModifiedHeaders(headers)
      target.get(testKey) must be(emptyReply)
    }

    s"prepare $IF_NONE_MATCH header if headers contain $ETAG value" in {
      val headers = mock[RememberedHeaders](answer({ _ => emptyReply }))
      val testValue = Map(ETAG -> Seq("bar"))
      when(headers.get(testKey)) thenReturn testValue

      val target = new RememberedEtagLastModifiedHeaders(headers)
      target.get(testKey) must be(Map(IF_NONE_MATCH -> Seq("bar")))
    }

    s"prepare $IF_MODIFIED_SINCE header if headers contain $LAST_MODIFIED value" in {
      val headers = mock[RememberedHeaders](answer({ _ => emptyReply }))
      val testValue = Map(LAST_MODIFIED -> Seq("bar"))
      when(headers.get(testKey)) thenReturn testValue

      val target = new RememberedEtagLastModifiedHeaders(headers)
      target.get(testKey) must be(Map(IF_MODIFIED_SINCE -> Seq("bar")))
    }
  }
}
