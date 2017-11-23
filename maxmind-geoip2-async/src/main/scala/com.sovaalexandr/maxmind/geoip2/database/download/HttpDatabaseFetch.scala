package com.sovaalexandr.maxmind.geoip2.database.download

import java.io.{File, FileOutputStream}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

import akka.dispatch.Futures.{failed, successful}
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import akka.util.ByteString
import com.sovaalexandr.maxmind.geoip2.database.{DatabaseFetch, DatabaseFile}

import scala.concurrent.ExecutionContextExecutor
// TODO: Change after moving of StandardValues.scala to import play.api.http.{HeaderNames, MimeTypes, Status}
import play.api.libs.ws.StandaloneWSResponse

import scala.concurrent.Future

object HttpDatabaseFetch {
  object CompressedBy extends Enumeration { val GZIP, DEFLATE, NONE = Value }
  private val pathPattern = Pattern.compile("""^.*(\.\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}?.\d{0,3})$""")
  private val suffixLength = 23 // dot and date like .2017-11-14T17:52:35.95

  def destinationPath(path: String, suffix: String = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)): String = {
    if (path.length <= HttpDatabaseFetch.suffixLength) s"$path.$suffix" else {
      val matcher = pathPattern.matcher(path)
      val prefix = if (matcher.find()) path.substring(0, path.length - matcher.group(1).length) else path

      s"$prefix.$suffix"
    }
  }
}

class HttpDatabaseFetch(requestProvider: DownloadRequest, decompressed: CompressedBy)(implicit m: Materializer) extends DatabaseFetch {

  implicit private val ec: ExecutionContextExecutor = m.executionContext

  override def fetchNow(dbFile: File): Future[DatabaseFile] = requestProvider(dbFile).stream()
    .flatMap({
      // TODO: Change after moving of StandardValues.scala to Status.OK and HeaderNames.CONTENT_TYPE
      case okResponse if 200 == okResponse.status => okResponse.header("Content-Type") match {
        // TODO: Change after moving of StandardValues.scala to MimeTypes.BINARY
        case Some("application/octet-stream") => grabTheFile(okResponse, HttpDatabaseFetch.destinationPath(dbFile.getPath))
        case Some(otherMimeType) => // TODO: Change after moving of StandardValues.scala to MimeTypes.BINARY
          failed(new DatabaseDownloadFailed(s"Wrong download mime type. Expected: application/octet-stream, got: $otherMimeType."))
        // TODO: Change after moving of StandardValues.scala to HeaderNames.CONTENT_TYPE
        case None => failed(new DatabaseDownloadFailed("No Content-Type header found. Can't download."))
      }
      // TODO: Change after moving of StandardValues.scala to Status.NOT_MODIFIED
      case notModified if 304 == notModified.status => successful(DatabaseFile())
      case failed => downloadFailed(failed)
    })

  private def grabTheFile(response: StandaloneWSResponse, destinationFileName: String): Future[DatabaseFile] = {
    val destinationFile = new File(destinationFileName)
    val destinationStream = new FileOutputStream(destinationFile)

    // The sink that writes to the output stream
    val sink = Sink.foreach[ByteString]({ bytes => destinationStream.write(bytes.toArray) })

    decompressed(response.bodyAsSource).runWith(sink).andThen({
      case result =>
        // Close the output stream whether there was an error or not
        destinationStream.close()
        // Get the result or rethrow the error
        result.get
    }).map(_ => DatabaseFile(destinationFile))
  }

  private def downloadFailed(failedResponse: StandaloneWSResponse): Future[DatabaseFile] = {
    failedResponse.bodyAsSource.toMat(Sink.fold(ByteString.empty)(_ ++ _))(Keep.right)
      .run().map({ _.utf8String })
      .map(message => throw new DatabaseDownloadFailed(s"Download failed: Http error (${failedResponse.status}), ${failedResponse.statusText}. Details: $message"))
  }
}
