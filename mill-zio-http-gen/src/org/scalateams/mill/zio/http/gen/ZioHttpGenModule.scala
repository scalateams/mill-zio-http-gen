package org.scalateams.mill.zio.http.gen

import mill.*
import mill.scalalib.*
import org.scalateams.mill.zio.http.gen.api.GenerationResult
import org.scalateams.mill.zio.http.gen.parsers.Parser
import os.RelPath
import zio.http.gen.scala.CodeGen

import scala.util.control.NonFatal

trait ZioHttpGenModule extends ScalaModule { outer: ScalaModule =>

  override def generatedSources: T[Seq[PathRef]] = Task {
    super.generatedSources() ++ Task.traverse(generators)(_.generate)().map(_.dest)
  }

  trait OpenAPIModule extends Module { inner: Module =>

    import zio.http.endpoint.openapi.OpenAPI
    import zio.http.gen.openapi.{Config, EndpointGen}

    /**
     * The folders containing all source files fed into the endpoint generator.
     */
    def allSources: T[Seq[PathRef]] = Task { sources() ++ generatedSources() }

    /**
     * All individual source files fed into the endpoint generator.
     */
    def allSourceFiles: T[Seq[PathRef]] = Task {
      Lib.findSourceFiles(allSources(), parsers.keys.toSeq).map(PathRef(_))
    }

    /**
     * Configuration for the OpenAPI endpoint generator.
     */
    def config: Config = Config.default

    def generate: T[GenerationResult] = Task {
      val _       = inner.inputHash()
      val config  = inner.config
      val prefix  = inner.packagePrefix()
      val sources = inner.allSourceFiles()
      val files   = sources.flatMap { source =>
        parsers.get(source.path.ext) match {
          case None         => Task.fail(s"No parser found for file extension: ${source.path.ext}")
          case Some(parser) =>
            val content     = os.read(source.path).getBytes("UTF-8")
            val openapi     = parser.parse[OpenAPI](content).fold(Task.fail, identity)
            val files       =
              try EndpointGen.fromOpenAPI(openapi, config)
              catch { case e if NonFatal(e) => Task.fail(e.getMessage) }
            val extra       =
              inner.sources().find(x => source.path.startsWith(x.path)) match {
                case Some(x) => ZioHttpGenModule.dirDiffToPackage(x.path, source.path)
                case None    => Seq.empty[String]
              }
            val basePackage = (prefix ++ extra).map(_.trim).filter(_.nonEmpty).mkString(".") match {
              case "" => Task.fail("Cannot generate code with empty package name.")
              case s  => s
            }
            Task.log.info(
              s"Generated ${files.files.size} file(s) from ${source.path} in package $basePackage",
            )
            CodeGen
              .renderedFiles(files, basePackage)
              .map { case (path, content) =>
                val outPath = Task.dest / RelPath(path.stripPrefix("/"))
                os.write.over(outPath, content, createFolders = true)
                PathRef(outPath)
              }
              .toSeq
        }
      }
      GenerationResult(PathRef(Task.dest), files)
    }

    /**
     * Folders containing source files that are generated rather than
     * handwritten; these files can be generated in this task itself, or can
     * refer to files generated from other tasks.
     */
    def generatedSources: T[Seq[PathRef]] = Task { Seq.empty[PathRef] }

    def packagePrefix: T[Seq[String]] = Task { Seq("org", "example") }

    /**
     * The parsers available for parsing OpenAPI files.
     */
    def parsers: Map[String, Parser] =
      Map(
        "json" -> Parser.BuiltInJsonParser,
        "yaml" -> Parser.BuiltInYamlParser,
        "yml"  -> Parser.BuiltInYamlParser,
      )

    /**
     * The folders where the source files for this module live. By default, this
     * evaluates to the module directory.
     */
    def sources: T[Seq[PathRef]] = Task.Sources(inner.moduleDir)

    protected def inputHash: T[Int] = Task.Input {
      val config  = inner.config
      val parsers = inner.parsers
      (config, parsers).hashCode()
    }
  }

  private def generators: Seq[OpenAPIModule] = moduleDirectChildren.collect { case m: OpenAPIModule => m }
}

object ZioHttpGenModule {

  private[http] def dirDiffToPackage(base: os.Path, file: os.Path): Seq[String] = {
    val relPath = file.relativeTo(base)
    relPath.segments.dropRight(1).map(_.toString)
  }
}
