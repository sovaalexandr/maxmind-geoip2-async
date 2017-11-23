package com.sovaalexandr.maxmind.geoip2.database.download.headers

import com.sovaalexandr.maxmind.geoip2.database.download.RememberedHeaders

import scala.collection.concurrent

class RememberedInMemoryHeaders extends RememberedHeaders {
  private val memory = concurrent.TrieMap[String, Map[String, Seq[String]]]()
  override def set(key: String, value: Map[String, Seq[String]]): Unit = memory += (key -> value)

  override def get(key: String): Map[String, Seq[String]] = memory.getOrElse(key, Map.empty)
}
