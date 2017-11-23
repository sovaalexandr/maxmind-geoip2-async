package com.sovaalexandr.maxmind.geoip2.database.download.headers

import com.sovaalexandr.MockitoAnswer
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{MustMatchers, WordSpec}
import play.api.cache.SyncCacheApi

import scala.concurrent.duration.Duration

class RememberedAtSyncCacheHeadersTest extends WordSpec with MustMatchers with MockitoSugar with MockitoAnswer {
  private val testKey = "someTestKey"
  private val testDuration = Duration.Inf

  "A RememberedAtSyncCacheHeaders" should {

    "get an empty Map if there is no cache entry by key" in {
      val cache = mock[SyncCacheApi](answer({ _ => None }))
      val target = new RememberedAtSyncCacheHeaders(cache, testDuration)
      target.get(testKey) must be(Map.empty[String, Seq[String]])
    }

    "get a Map if there is a cache entry by key" in {
      val cache = mock[SyncCacheApi](answer({ _ => None }))
      val testValue = Map("key1" -> Seq("foo", "bar", "baz"))
      when(cache.get[Map[String, Seq[String]]](testKey)) thenReturn Some(testValue)

      val target = new RememberedAtSyncCacheHeaders(cache, testDuration)

      target.get(testKey) must be(testValue)
    }

    "add an entry to cache if there is no cache entry by key" in {
      val cache = mock[SyncCacheApi]
      when(cache.get[Map[String, Seq[String]]](testKey)) thenReturn None
      val target = new RememberedAtSyncCacheHeaders(cache, testDuration)

      val testValue = Map("key1" -> Seq("foo", "bar", "baz"))
      target.set(testKey, testValue)

      /* May be useless because Scala version of Mockito treats this call as no-argument call and matches with any call
       * to set method */
      verify(cache).set(testKey, testValue, testDuration)
    }
    "add an entry to cache if there is a cache entry by key and there are no intersecting sub-keys" in {
      val cache = mock[SyncCacheApi]
      val testValue = Map("key1" -> Seq("foo", "bar", "baz"))
      when(cache.get[Map[String, Seq[String]]](testKey)) thenReturn Some(testValue)

      val target = new RememberedAtSyncCacheHeaders(cache, testDuration)

      target.set(testKey, Map("key2" -> Seq("foo", "bar", "baz")))

      val expecting = Map(
        "key1" -> Seq("foo", "bar", "baz"),
        "key2" -> Seq("foo", "bar", "baz")
      )
      /* May be useless because Scala version of Mockito treats this call as no-argument call and matches with any call
       * to set method */
      verify(cache).set(testKey, expecting, testDuration)
    }
    "replace an entry at cache if there is a cache entry by key and there are some intersecting sub-keys" in {
      val cache = mock[SyncCacheApi]
      val testValue = Map("key1" -> Seq("foo", "bar", "baz"))
      when(cache.get[Map[String, Seq[String]]](testKey)) thenReturn Some(testValue)

      val target = new RememberedAtSyncCacheHeaders(cache, testDuration)

      target.set(testKey, Map("key1" -> Seq("something", "other")))

      val expecting = Map("key1" -> Seq("something", "other"))
      /* May be useless because Scala version of Mockito treats this call as no-argument call and matches with any call
       * to set method */
      verify(cache).set(testKey, expecting, testDuration)
    }
  }
}
