import sbt._

object Dependencies {
  val playWsStandalone: ModuleID = "com.typesafe.play" %% "play-ahc-ws-standalone" % "1.1.3"
  val playCache: ModuleID = "com.typesafe.play" %% "play-cache" % "2.6.7"
  val maxmindGeoIP2DB: ModuleID = "com.maxmind.geoip2" % "geoip2" % "2.9.0"
  val akkaPersistence: ModuleID = "com.typesafe.akka" %% "akka-persistence" % "2.5.6"

  def all = Seq(playWsStandalone, maxmindGeoIP2DB, akkaPersistence, playCache)
}

object TestDependencies {
  val mockito: ModuleID = "org.mockito" % "mockito-core" % "2.11.0" % Test
  val scalaTest: ModuleID = "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
  val akkaTestKit: ModuleID = "com.typesafe.akka" %% "akka-testkit" % "2.5.6" % Test
  val testLogback: ModuleID = "ch.qos.logback" % "logback-classic" % "1.2.3" % Test
  val akkaHttp: ModuleID = "com.typesafe.akka" %% "akka-http" % "10.0.8" % Test

  def unit = Seq(mockito, scalaTest, akkaTestKit, testLogback)
  def functional = Seq(akkaHttp)
}

object IntegrationDependencies {
  val guice: ModuleID = "com.google.inject" % "guice" % "4.1.0"
}