package com.sovaalexandr.maxmind.geoip2.database

import com.typesafe.config.{Config, ConfigFactory}

object AkkaSettings {
  private val configuration = """
akka {
   loglevel="DEBUG"
   actor {
     debug {
       receive = on
       autoreceive = on
     }
   }
   persistence.snapshot-store.local.dir = """"+System.getProperty("java.io.tmpdir")+"""/DatabaseFileProviderTest/snapshots"
}
""".stripMargin
  def config: Config = ConfigFactory.parseString(configuration)
}
