package org.scalateams.mill.zio.http.gen

import mill.*
import mill.scalalib.*
import org.scalateams.mill.zio.http.gen.api.GenerationResult
import org.scalateams.mill.zio.http.gen.parsers.{Parser, ParserRef}
import os.RelPath
import zio.http.gen.scala.CodeGen

import scala.util.control.NonFatal

trait ZioHttpGenModule extends ScalaModule { outer: ScalaModule =>

  override def generatedSources: T[Seq[PathRef]] = Task {
    super.generatedSources() ++ Task.traverse(generatorModules)(_.generate)().map(_.dest)
  }

  def generatorModules: Seq[GeneratorModule] = {
    def recur(module: Module): Seq[GeneratorModule] = module match {
      case m: GeneratorModule => Seq(m)
      case m: Module          => m.moduleDirectChildren.flatMap(recur)
    }
    recur(outer)
  }

  trait GeneratorModule extends Module { inner: Module =>

    /**
     * The folders containing all source files fed into the endpoint generator.
     */
    def allSources: T[Seq[PathRef]] = Task { sources() ++ generatedSources() }

    /**
     * All individual source files fed into the endpoint generator.
     */
    def allSourceFiles: T[Seq[PathRef]] = Task {
      Lib.findSourceFiles(allSources(), parsers().keys.toSeq).map(PathRef(_))
    }

    /**
     * The main code generation task. This should read the source files from
     * `allSourceFiles`, parse them using the appropriate parsers from
     * `parsers`, generate code based on the parsed representations, and write
     * the generated files to `Task.dest`. It should then return a
     * `GenerationResult` containing the destination folder and the list of
     * generated files.
     */
    def generate: T[GenerationResult]

    /**
     * Folders containing source files that are generated rather than
     * handwritten; these files can be generated in this task itself, or can
     * refer to files generated from other tasks.
     */
    def generatedSources: T[Seq[PathRef]] = Task { Seq.empty[PathRef] }

    def packagePrefix: T[Seq[String]]

    /**
     * A mapping from file extensions to parsers, used to determine how to parse
     * the source files fed into the generator.
     */
    def parsers: T[Map[String, ParserRef]]

    /**
     * The folders where the source files for this module live. By default, this
     * evaluates to the module directory.
     */
    def sources: T[Seq[PathRef]] = Task.Sources(inner.moduleDir)
  }

  trait OpenAPIModule extends GeneratorModule { inner: Module =>

    import zio.http.endpoint.openapi.OpenAPI
    import zio.http.gen.openapi.{Config, EndpointGen}

    given upickle.default.ReadWriter[Config.NormalizeFields] = upickle.default.macroRW
    given upickle.default.ReadWriter[Config]                 = upickle.default.macroRW

    /**
     * Configuration for the OpenAPI endpoint generator.
     */
    def config: T[Config] = Task { Config.default }

    override def generate: T[GenerationResult] = Task {
      val config  = inner.config()
      val prefix  = inner.packagePrefix()
      val sources = inner.allSourceFiles()
      val parsers = inner.parsers()
      val files   = sources.flatMap { source =>
        parsers.get(source.path.ext) match {
          case None            => Task.fail(s"No parser found for file extension: ${source.path.ext}")
          case Some(parserRef) =>
            val parser      =
              try ParserRef.resolve(parserRef)
              catch { case e if NonFatal(e) => Task.fail(e.getMessage) }
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

    override def parsers: T[Map[String, ParserRef]] = Task {
      Map(
        "json" -> ParserRef.of(Parser.json),
        "yaml" -> ParserRef.of(Parser.yaml),
        "yml"  -> ParserRef.of(Parser.yaml),
      )
    }
  }
}

object ZioHttpGenModule {

  private[gen] def dirDiffToPackage(base: os.Path, file: os.Path): Seq[String] = {
    val relPath = base.relativeTo(file / os.up)
    relPath.segments.map(_.toString)
  }
}
