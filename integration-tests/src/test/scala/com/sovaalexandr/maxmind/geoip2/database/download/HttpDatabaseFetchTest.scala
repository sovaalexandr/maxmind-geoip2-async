package com.sovaalexandr.maxmind.geoip2.database.download

import java.io.File
import java.time.{ZoneId, ZonedDateTime}

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import akka.http.scaladsl.server.Route
import akka.util.ByteString
import com.sovaalexandr.AkkaServerProvider
import com.sovaalexandr.maxmind.geoip2.database.CurrentDatabaseFile
import com.sovaalexandr.maxmind.geoip2.database.download.headers.{RememberedEtagLastModifiedHeaders, RememberedInMemoryHeaders}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{MustMatchers, WordSpecLike}
import play.api.libs.ws.ahc.{AhcWSClientConfig, AhcWSClientConfigFactory, StandaloneAhcWSClient}
import play.api.mvc.ResponseHeader
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

class HttpDatabaseFetchTest extends WordSpecLike with MustMatchers with DefaultAwaitTimeout with FutureAwaits with MockitoSugar
  with AkkaServerProvider
{
  private val lastModified = ZonedDateTime.of(2017, 10, 11, 23, 35, 15, 42, ZoneId.systemDefault())
  private val testFile = mock[File]
  when(testFile.isFile) thenReturn true
  when(testFile.getPath) thenReturn s"${System.getProperty("java.io.tmpdir")}/GeoLite2-City.mmdb"

  override def routes: Route = {
    import akka.http.scaladsl.server.Directives._
    get {
      path("database" / "GeoLite2-City.mmdb") {
        headerValueByName("If-None-Match") {
          case "testETag" => complete(StatusCodes.NotModified)
        } ~ {
          complete(HttpEntity(ContentTypes.`application/octet-stream`, ByteString.empty))
        }
      } ~
      path("database" / "GeoLite2-City.csv") {
        complete(HttpEntity(ContentTypes.`text/csv(UTF-8)`, ByteString.empty))
      }
    }
  }

  def withClient(config: AhcWSClientConfig = AhcWSClientConfigFactory.forConfig())(block: StandaloneAhcWSClient => Any): Any = {
    val client = StandaloneAhcWSClient(config)
    try {
      block(client)
    } finally {
      client.close()
    }
  }

  def withDbFileClient(uri: String, block: (HttpDatabaseFetch) => Any, headers: RememberedInMemoryHeaders = new RememberedInMemoryHeaders()): Any = {
    withClient() { client => block(new HttpDatabaseFetch(
      new DownloadRequest(client, DownloadRequest.Settings(uri), new RememberedHeadersFilter(new RememberedEtagLastModifiedHeaders(headers))(system.dispatcher)),
      CompressedByNONE
    )(materializer))
    }
  }

  "A HttpDatabaseFetch" when {

    "asked destinationPath from" should {
      val shortWithoutSuffix = "/tmp/GeoLite2-City.mmdb"
      val suffix = "2017-11-14T17:52:35.166"

      s"short path $shortWithoutSuffix at $suffix" should {
        s"result $shortWithoutSuffix.$suffix" in {
          HttpDatabaseFetch.destinationPath(shortWithoutSuffix, suffix) must be(s"$shortWithoutSuffix.$suffix")
        }
      }

      val withSuffix = s"$shortWithoutSuffix.$suffix"
      val otherSuffix = "2017-11-14T17:52:35.160"

      s"short path $withSuffix at $otherSuffix" should {
        s"result $shortWithoutSuffix.$otherSuffix" in {
          HttpDatabaseFetch.destinationPath(shortWithoutSuffix, otherSuffix) must be(s"$shortWithoutSuffix.$otherSuffix")
        }
      }

      val longWithoutSuffix = "/tmp/some/kind/of/long/path/that/is/longer/than/date/GeoLite2-City.mmdb"

      s"long path $longWithoutSuffix at $otherSuffix" should {
        s"result $longWithoutSuffix.$otherSuffix" in {
          HttpDatabaseFetch.destinationPath(longWithoutSuffix, otherSuffix) must be(s"$longWithoutSuffix.$otherSuffix")
        }
      }

      val longWithSuffix = "/tmp/GeoLite2-City.mmdb.2017-11-21T14:25:13.95"
      val thirdSuffix = "2017-11-21T14:25:13.95"

      s"long path $longWithSuffix at $thirdSuffix" should {
        s"result $longWithSuffix.$thirdSuffix" in {
          HttpDatabaseFetch.destinationPath(longWithSuffix, thirdSuffix) must be(s"$longWithSuffix")
        }
      }
    }

    "file is present" should {
      "fetch an encrypted DB file without cache" in withDbFileClient(s"http://localhost:$testServerPort/database/GeoLite2-City.mmdb", { target =>
        val databaseFile = await(target.fetchNow(testFile))
        val probe = mock[CurrentDatabaseFile]
        databaseFile(probe) mustBe null
        verify(probe).gotNewOne(any[File])
        verify(probe, never()).stillFine()
      })

      val headers = new RememberedInMemoryHeaders
      "not fetch an encrypted DB file if cache is still valid" in withDbFileClient(s"http://localhost:$testServerPort/database/GeoLite2-City.mmdb", { target =>
        headers.set(s"http://localhost:$testServerPort/database/GeoLite2-City.mmdb", Map(
          "ETag" -> Seq("testETag"),
          "Last-Modified" -> Seq(lastModified.format(ResponseHeader.httpDateFormat))
        ))
        val databaseFile = await(target.fetchNow(testFile))
        val probe = mock[CurrentDatabaseFile]
        databaseFile(probe) mustBe null
        verify(probe, never()).gotNewOne(any[File])
        verify(probe).stillFine()
      }, headers)

      "fail with DatabaseDownloadFailed exception if download Content-Type is not application/octet-stream" in withDbFileClient(s"http://localhost:$testServerPort/database/GeoLite2-City.csv", { target =>
        val downloadFailed = intercept[DatabaseDownloadFailed]{await(target.fetchNow(testFile))}
        downloadFailed.message must be(s"Wrong download mime type. Expected: application/octet-stream, got: text/csv; charset=UTF-8.")
      })

      "fail with DatabaseDownloadFailed exception if download not completes successfully" in withDbFileClient(s"http://localhost:$testServerPort/database/uri/not/exist", { target =>
        val downloadFailed = intercept[DatabaseDownloadFailed]{await(target.fetchNow(testFile))}
        downloadFailed.message must startWith(s"Download failed: Http error (404), Not Found. Details:")
      })
    }
  }
}
