package com.sovaalexandr.maxmind.geoip2

import java.net.InetAddress

trait GeoIP2Request {
}

case class City(ofIp: InetAddress) extends GeoIP2Request

case class Country(ofIp: InetAddress) extends GeoIP2Request

case object Idle

case object AddressNotFound
