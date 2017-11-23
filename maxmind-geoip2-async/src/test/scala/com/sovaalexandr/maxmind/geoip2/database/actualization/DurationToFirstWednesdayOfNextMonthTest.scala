package com.sovaalexandr.maxmind.geoip2.database.actualization

import java.time.LocalDateTime

import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.duration.{Duration, FiniteDuration}

class DurationToFirstWednesdayOfNextMonthTest extends WordSpecLike with MustMatchers {


  "A DurationToFirstWednesdayOfNextMonth" when {
    val target = new DurationToFirstWednesdayOfNextMonth
    "got small startPoint" should {
      val startPoint = LocalDateTime.of(2017, 11, 12, 4, 3, 2)
      "provide FiniteDuration" in {
        target(startPoint).isInstanceOf[FiniteDuration] must be(true)
      }
    }
    "got too far start point" should {
      val startPoint = LocalDateTime.MIN
      "provide zero duration" in {
        target(startPoint) must be(Duration.fromNanos(0L))
      }
    }
  }
}
