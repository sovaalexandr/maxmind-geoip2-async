import sbt.Keys.testOptions

name := "geolocation"

val javacSettings = Seq(
  "-source", "1.8",
  "-target", "1.8",
  "-Xlint:deprecation",
  "-Xlint:unchecked"
)

lazy val commonSettings = Seq(
  organization := "com.sovaalexandr",
  scalaVersion := "2.12.4",
  startYear := Some(2015),
  pomExtra :=
<url>https://github.com/sovaalexandr/maxmind-geoip2-async</url>
<licenses>
  <license>
    <name>Apache License, Version 2.0</name>
    <url>http://www.apache.org/licenses/LICENSE-2.0</url>
    <distribution>repo</distribution>
  </license>
</licenses>
<scm>
  <url>git@github.com:sovaalexandr/maxmind-geoip2-async.git</url>
  <connection>scm:git:git@github.com:sovaalexandr/maxmind-geoip2-async.git</connection>
</scm>
<developers>
  <developer>
    <id>sovaalexandr</id>
    <name>Oleksandr Sova</name>
    <url>https://github.com/sovaalexandr</url>
  </developer>
</developers>,
  javacOptions in (Compile, doc) ++= javacSettings,
  javacOptions in Test ++= javacSettings,
  javacOptions in IntegrationTest ++= javacSettings
)

val disableDocs = Seq[Setting[_]](
  sources in (Compile, doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) := false
)

val disablePublishing = Seq[Setting[_]](
  publishArtifact := false,
  // The above is enough for Maven repos but it doesn't prevent publishing of ivy.xml files
  publish := {},
  publishLocal := {}
)

lazy val root = (project in file("."))
  .aggregate(`maxmind-geoip2-async`, `maxmind-geoip2-async-guice`, `playframeworkExample`, `integration-tests`)
  .settings(commonSettings)
  .settings(disableDocs)
  .settings(disablePublishing)

lazy val `maxmind-geoip2-async` = (project in file("maxmind-geoip2-async"))
  .settings(libraryDependencies ++= Dependencies.all)
  .settings(libraryDependencies ++= TestDependencies.unit)
  .settings(commonSettings)

lazy val `maxmind-geoip2-async-guice` = (project in file("maxmind-geoip2-async-guice"))
  .settings(commonSettings)
  .settings(libraryDependencies += IntegrationDependencies.guice)
  .dependsOn(`maxmind-geoip2-async`)

lazy val `playframeworkExample` = (project in file("sample/play"))
  .enablePlugins(PlayMinimalJava)
  .settings(commonSettings)
  .settings(disableDocs)
  .settings(disablePublishing)
  .settings(routesGenerator := InjectedRoutesGenerator)
  .settings(libraryDependencies ++= Seq(
    ehcache, ws, guice
  ))
  .dependsOn(`maxmind-geoip2-async-guice`)

lazy val `integration-tests` = (project in file("integration-tests"))
  .settings(
    fork in Test := true,
    concurrentRestrictions += Tags.limitAll(1), // only one integration test at a time
    testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v"))
  )
  .settings(libraryDependencies ++= TestDependencies.functional)
  .settings(commonSettings)
  .settings(disableDocs)
  .settings(disablePublishing)
  .dependsOn(`maxmind-geoip2-async` % "compile->compile;test->test")
