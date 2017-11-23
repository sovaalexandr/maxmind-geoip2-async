package com.sovaalexandr.maxmind.geoip2.database.download

import akka.stream.scaladsl.{Compression, Source}
import akka.util.ByteString

sealed trait LimitedCompressedBy extends ((Int, Source[ByteString, _]) => Source[ByteString, _])

object LimitedCompressedByGZIP extends LimitedCompressedBy {
  override def apply(bytesPerChunk: Int, flow: Source[ByteString, _]): Source[ByteString, _] =
    flow.via(Compression.gunzip(bytesPerChunk))
}

object LimitedCompressedByDEFLATE extends LimitedCompressedBy {
  override def apply(bytesPerChunk: Int, flow: Source[ByteString, _]): Source[ByteString, _] =
    flow.via(Compression.inflate(bytesPerChunk))
}

sealed abstract class CompressedBy extends (Source[ByteString, _] => Source[ByteString, _]) {}

class CompressedByGZIP(bytesPerChunk: Int) extends CompressedBy {
  override def apply(flow: Source[ByteString, _]): Source[ByteString, _] =
    LimitedCompressedByGZIP.curried(bytesPerChunk)(flow)
}

class CompressedByDEFLATE(bytesPerChunk: Int) extends CompressedBy {
  override def apply(flow: Source[ByteString, _]): Source[ByteString, _] =
    LimitedCompressedByDEFLATE.curried(bytesPerChunk)(flow)
}

object CompressedByNONE extends CompressedBy {
  override def apply(flow: Source[ByteString, _]): Source[ByteString, _] = flow
}
