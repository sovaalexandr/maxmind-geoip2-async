package com.sovaalexandr.maxmind.geoip2.database.download

import play.api.libs.ws.{WSRequestExecutor, WSRequestFilter}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class RememberedHeadersFilter(headers: RememberedHeaders)(implicit ec: ExecutionContext) extends WSRequestFilter {
  override def apply(executor: WSRequestExecutor): WSRequestExecutor = {
    WSRequestExecutor { request =>
      import scala.collection.breakOut
      val forUrl = headers.get(request.url)
        .flatMap({ case (headerName, headerValues) => headerValues.map(headerName -> _) })(breakOut)

      val eventualResponse = executor(if (forUrl.isEmpty) request else request.addHttpHeaders(forUrl:_*))
      eventualResponse.onComplete({
        case Failure(_) => // Do nothing. Failures handled elsewhere
        case Success(response) =>
          // TODO: Change after moving of StandardValues.scala to: if (Status.OK == response.status && response.headers.nonEmpty) { // Do not change ETag and Last Modified if status is NotModified
          if (200 == response.status && response.headers.nonEmpty) { // Do not change ETag and Last Modified if status is NotModified
            headers.set(request.url, response.headers)
          }
      })

      eventualResponse
    }
  }
}
