package org.scalateams.mill.zio.http.gen.parsers

import org.scalateams.mill.zio.http.gen.BuildInfo
import zio.schema.Schema

trait Parser {
  def id: String
  def parse[A: Schema](input: Array[Byte]): Either[String, A]
}

object Parser {

  object json extends Parser {

    override val id: String = "json-" + BuildInfo.version

    override def parse[A: Schema](input: Array[Byte]): Either[String, A] = {
      import zio.schema.codec.JsonCodec
      JsonCodec.jsonDecoder(Schema[A]).decodeJson(new String(input, "UTF-8"))
    }
  }

  object yaml extends Parser {

    override val id: String = "yaml-" + BuildInfo.version

    override def parse[A: Schema](input: Array[Byte]): Either[String, A] = {
      import zio.json.yaml.DecoderYamlOps
      import zio.schema.codec.JsonCodec

      val content = new String(input, "UTF-8")
      content.fromYaml(using JsonCodec.jsonDecoder(Schema[A]))
    }
  }
}

final case class ParserRef private[parsers] (id: String, className: String) derives upickle.default.ReadWriter

object ParserRef {

  import scala.reflect.{classTag, ClassTag}

  def of[P <: Parser: ClassTag](parser: P): ParserRef = new ParserRef(parser.id, classTag[P].runtimeClass.getName)

  private[gen] def resolve(ref: ParserRef): Parser = {
    val cls = Class.forName(ref.className)
    if (ref.className.endsWith("$"))
      cls.getField("MODULE$").get(null).asInstanceOf[Parser]          // scalafix:ok
    else
      cls.getDeclaredConstructor().newInstance().asInstanceOf[Parser] // scalafix:ok
  }
}
