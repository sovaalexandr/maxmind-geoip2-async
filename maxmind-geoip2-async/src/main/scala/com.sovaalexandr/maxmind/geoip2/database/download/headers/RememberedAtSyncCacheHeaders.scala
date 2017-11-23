package com.sovaalexandr.maxmind.geoip2.database.download.headers

import com.sovaalexandr.maxmind.geoip2.database.download.RememberedHeaders
import play.api.cache.SyncCacheApi

import scala.concurrent.duration.Duration

class RememberedAtSyncCacheHeaders(cache: SyncCacheApi, during: Duration) extends RememberedHeaders {
  override def set(key: String, value: Map[String, Seq[String]]): Unit = cache.set(key, get(key) ++ value, during)

  override def get(key: String): Map[String, Seq[String]] =
    /* Have to specify all the type and braces boilerplate because Mockito matches two arguments here:
     * one for key and one for type of value as Java class tag */
    cache.get[Map[String, Seq[String]]](key).getOrElse(Map.empty[String, Seq[String]])
}
