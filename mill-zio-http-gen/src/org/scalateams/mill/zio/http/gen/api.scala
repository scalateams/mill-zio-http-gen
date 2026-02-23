package org.scalateams.mill.zio.http.gen.api

import mill.api.PathRef

final case class GenerationResult(dest: PathRef, files: Seq[PathRef]) derives upickle.default.ReadWriter
