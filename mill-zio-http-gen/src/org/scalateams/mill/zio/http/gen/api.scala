package org.scalateams.mill.zio.http.gen.api

import mill.api.PathRef

case class GenerationResult(dest: PathRef, files: Seq[PathRef])

object GenerationResult {

  implicit val jsonFormatter: upickle.default.ReadWriter[GenerationResult] =
    upickle.default.macroRW
}
