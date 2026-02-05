package org.scalateams.mill.zio.http.gen.parsers

import zio.schema.Schema

trait Parser {
  def parse[A: Schema](input: Array[Byte]): Either[String, A]
}

object Parser {

  object BuiltInJsonParser extends Parser {

    override def parse[A: Schema](input: Array[Byte]): Either[String, A] = {
      import zio.schema.codec.JsonCodec
      JsonCodec.jsonDecoder(Schema[A]).decodeJson(new String(input, "UTF-8"))
    }
  }

  object BuiltInYamlParser extends Parser {

    override def parse[A: Schema](input: Array[Byte]): Either[String, A] = {
      import zio.json.yaml.DecoderYamlOps
      import zio.schema.codec.JsonCodec

      val content = new String(input, "UTF-8")
      content.fromYaml(using JsonCodec.jsonDecoder(Schema[A]))
    }
  }
}
