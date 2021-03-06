import sbt.Keys.testOptions
import sbt.url

name := "geolocation"

val javacSettings = Seq(
  "-source", "1.8",
  "-target", "1.8",
  "-Xlint:deprecation",
  "-Xlint:unchecked"
)

lazy val commonSettings = Seq(
  organization := "com.github.sovaalexandr",
  scalaVersion := "2.12.4",
  startYear := Some(2015),
  javacOptions in (Compile, doc) ++= javacSettings,
  javacOptions in Test ++= javacSettings,
  javacOptions in IntegrationTest ++= javacSettings
)

val disableDocs = Seq[Setting[_]](
  sources in (Compile, doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) := false
)

val publishing = Seq(
  publishTo := Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging),
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://github.com/sovaalexandr/maxmind-geoip2-async")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/sovaalexandr/maxmind-geoip2-async"),
      "scm:git@github.com:sovaalexandr/maxmind-geoip2-async.git"
    )
  ),
  developers := List(Developer(id="sovaalexandr", name="Oleksandr Sova", email="sovaalexandr@gmail.com", url=url("https://github.com/sovaalexandr")))
)

val disablePublishing = Seq[Setting[_]](
  skip in publish := true,
  publishArtifact := false,
  // The above is enough for Maven repos but it doesn't prevent publishing of ivy.xml files
  publish := {},
  publishLocal := {}
)

lazy val root = (project in file("."))
  .aggregate(
    `maxmind-geoip2-async`,
    `maxmind-geoip2-async-guice`,
    `maxmind-geoip2-async-compile-time-di`,
    `playframeworkExample`,
    `integration-tests`
  )
  .settings(commonSettings)
  .settings(disableDocs)
  .settings(disablePublishing)

lazy val `maxmind-geoip2-async` = (project in file("maxmind-geoip2-async"))
  .settings(libraryDependencies ++= Dependencies.all)
  .settings(libraryDependencies ++= TestDependencies.unit)
  .settings(publishing)
  .settings(commonSettings)

lazy val `maxmind-geoip2-async-compile-time-di` = (project in file("maxmind-geoip2-async-compile-time-di"))
  .settings(commonSettings)
  .settings(publishing)
  .dependsOn(`maxmind-geoip2-async`)

lazy val `maxmind-geoip2-async-guice` = (project in file("maxmind-geoip2-async-guice"))
  .settings(commonSettings)
  .settings(libraryDependencies += IntegrationDependencies.guice)
  .settings(publishing)
  .dependsOn(`maxmind-geoip2-async-compile-time-di`)

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

import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommand("publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)
