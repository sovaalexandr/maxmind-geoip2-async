package com.sovaalexandr.maxmind.geoip2.database.download

class DatabaseDownloadFailed(val message: String) extends RuntimeException(message) {
}
