package com.sovaalexandr.maxmind.geoip2.database.actualization;

import scala.Function1;
import scala.concurrent.duration.FiniteDuration;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 *  {@see https://dev.maxmind.com/geoip/geoip2/geolite2/#Databases}
 *  GeoLite2 databases are updated on the first Tuesday of each month.
 */
public class DurationToFirstWednesdayOfNextMonth implements Function1<LocalDateTime, FiniteDuration> {

  private static final long MAX_SECONDS = 9223372036L;

  @Override
  public FiniteDuration apply(final LocalDateTime startPoint) {

    LocalDateTime firstWednesdayOfNextMonth = startPoint
      .with(TemporalAdjusters.firstDayOfNextMonth())
      .with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));
    Duration duration = Duration.between(LocalDateTime.now(), firstWednesdayOfNextMonth);

    return scala.concurrent.duration.Duration.fromNanos(Math.abs(duration.getSeconds()) > MAX_SECONDS ? 0L : duration.toNanos());
  }
}
