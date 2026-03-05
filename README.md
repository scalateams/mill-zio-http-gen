# mill-zio-http-gen

![CI passing badge](https://github.com/scalateams/mill-zio-http-gen/actions/workflows/ci.yml/badge.svg?branch=main) ![Maven Central version badge](https://img.shields.io/maven-central/v/org.scalateams/mill-zio-http-gen_mill1_3) [![Scaladoc badge](https://img.shields.io/badge/Scaladoc-gray)](https://javadoc.io/doc/org.scalateams/mill-zio-http-gen_mill1_3/latest/org.scalateams.mill.zio.http.gen/index.html) [![Scala Steward helping badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://github.com/scala-steward-org/scala-steward) [![GitHub stars badge](https://img.shields.io/github/stars/scalateams/mill-zio-http-gen?style=social)](https://github.com/scalateams/mill-zio-http-gen)

A [zio-http-gen](https://github.com/zio/zio-http) plugin for Mill build tool.

## Usage

*build.mill*:
```scala
//| mvnDeps:
//| - org.scalateams::mill-zio-http-gen::0.1.1
import mill.scalalib.*
import org.scalateams.mill.zio.http.gen.ZioHttpGenModule

object project extends ScalaModule with ZioHttpGenModule {

  def mvnDeps      = super.mvnDeps() ++ Seq(mvn"dev.zio::zio-http:3.9.0")
  def scalaVersion = "3.3.7"

  object openapi extends OpenAPIModule {

    def packagePrefix = Task { Seq("com", "example") }
  }
}
```

```shell script
> mill project.compile
Generated 2 file(s) from .../openapi/openapi.yaml in package org.example
Compiling compiler interface...
...
done compiling
```

### Overriding default configuration

*build.mill*:
```scala
object openapi extends OpenAPIModule {

  import org.scalateams.mill.zio.http.gen.parsers.Parser
  import zio.http.gen.openapi.Config
  
  def config        = Task { Config.default.copy(commonFieldsOnSuperType = true) }
  def packagePrefix = Task { Seq("com", "example") }
  def parsers       = Task { Map("yaml" -> ParserRef.of[Parser.yaml])
  def sources       = Task.Sources("openapi")
}
```

### Providing specification dynamically

```scala
object openapi extends OpenAPIModule {

  import mill.*

  def packagePrefix    = Task { Seq("com", "example") }
  def specification    = Task {
    """openapi: 3.1.0
      |info:
      |  title: User API
      |  version: 1.0.0
      |paths:""".stripMargin
  }
  def generatedSources = Task {
    val content = specification() // think of downloading etc.
    os.write.over(Task.dest / Path("openapi.yaml"), content, createFolders = true)
    Seq(PathRef(Task.dest))
  }
}
```

### Multiple generators

```scala
object project extends ScalaModule with ZioHttpGenModule { self =>

  object openapi extends OpenAPIModule {

    def packagePrefix = Task { Seq("com", "example") }
  }

  object openapi_internal extends OpenAPIModule {

    import mill.*

    def packagePrefix = openapi.packagePrefix() ++ Seq("internal")
    def sources       = Task.Sources(self.moduleDir / "internal" / "openapi")
  }
}
```

## Related projects

* [zio-http](https://github.com/zio/zio-http)
* Inspired by [zio-http-sbt](https://github.com/zio/zio-http-sbt)

## Contributing

Before committing run:

```console
$ ./mill __.style + __.test + __.publishLocal
```

All contributions are welcome!
