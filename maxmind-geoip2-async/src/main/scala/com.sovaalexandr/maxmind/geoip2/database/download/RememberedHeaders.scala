package com.sovaalexandr.maxmind.geoip2.database.download

/**
 * Interface for collection of http headers.
 * api is sync because I don't think that it is a good idea to store such values as http headers at a shared storage
 * which may require async access.
 */
trait RememberedHeaders {
  def set(key: String, value: Map[String, Seq[String]]): Unit

  def get(key: String): Map[String, Seq[String]]
}
