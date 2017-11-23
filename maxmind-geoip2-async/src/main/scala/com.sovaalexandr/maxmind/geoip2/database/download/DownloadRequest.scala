package com.sovaalexandr.maxmind.geoip2.database.download

import java.io.File

import com.sovaalexandr.maxmind.geoip2.database.download.DownloadRequest.Settings
import play.api.libs.ws.{StandaloneWSRequest, StandaloneWSClient}

object DownloadRequest {
  case class Settings(baseUrl: String)
}

class DownloadRequest (ws: StandaloneWSClient, settings: Settings, filter: RememberedHeadersFilter) extends (File => StandaloneWSRequest) {
  override def apply(dbFile: File): StandaloneWSRequest = {
    val request = ws.url(settings.baseUrl).withMethod("GET") // TODO: Change after moving of StandardValues.scala to (HttpVerbs.GET)
    if (dbFile.isFile) request.withRequestFilter(filter) else request
  }
}
