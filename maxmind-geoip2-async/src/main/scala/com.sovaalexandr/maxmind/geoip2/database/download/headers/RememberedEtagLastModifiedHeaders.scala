package com.sovaalexandr.maxmind.geoip2.database.download.headers

import com.sovaalexandr.maxmind.geoip2.database.download.RememberedHeaders

object RememberedEtagLastModifiedHeaders {
  private val acceptedHeaders = Seq("ETag", "Last-Modified") // TODO: Change after moving of StandardValues.scala to Seq(ETAG, LAST_MODIFIED)
  private val accepts = acceptedHeaders.contains(_: String)
}

class RememberedEtagLastModifiedHeaders(headers: RememberedHeaders) extends RememberedHeaders {
  override def set(key: String, candidate: Map[String, Seq[String]]): Unit = {
    val filtered = candidate.filterKeys(RememberedEtagLastModifiedHeaders.accepts)
    if (filtered.nonEmpty) {
      headers.set(key, filtered)
    }
  }

  override def get(key: String): Map[String, Seq[String]] =
    // Ignore compiler warning. filterKeys will do the job.
    headers.get(key).filterKeys(RememberedEtagLastModifiedHeaders.accepts).map({
      case ("ETag", values) => "If-None-Match" -> values // TODO: Change after moving of StandardValues.scala to case (ETAG, values) => IF_NONE_MATCH -> values
      case ("Last-Modified", values) => "If-Modified-Since" -> values // TODO: Change after moving of StandardValues.scala to case (LAST_MODIFIED, values) => IF_MODIFIED_SINCE -> values
    })
}
