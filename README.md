# mill-zio-http-gen

A [zio-http-gen](https://github.com/zio/zio-http) plugin for Mill build tool.

## Usage

*build.mill*:
```scala
//| mvnDeps:
//| - org.scalateams::mill-zio-http-gen::0.1.0
import org.scalateams.mill.zio.http.gen.ZioHttpGenModule
import mill.scalalib.*

object project extends ScalaModule with ZioHttpGenModule {

  def mvnDeps      = super.mvnDeps() ++ Seq(mvn"dev.zio::zio-http:3.8.0")
  def scalaVersion = "3.3.7"

  object openapi extends OpenAPIModule
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
  
  def config        = Config.default.copy(commonFieldsOnSuperType = true)
  def packagePrefix = Task { Seq("com", "example") }
  def parsers       = Map("yaml" -> Parser.BuiltInYamlParser)
  def sources       = Task.Sources("openapi")
}
```

### Providing specification dynamically

```scala
object openapi extends OpenAPIModule {

  import mill.*

  def specification    = Task { "openapi: 3.1.0\ninfo:\n  title: User API\n  version: 1.0.0\npaths:\n" }
  def generatedSources = Task {
    val content = specification()
    os.write.over(Task.dest / Path("openapi.yaml"), content, createFolders = true)
    Seq(PathRef(Task.dest))
  }
}
```

### Multiple generators

```scala
object project extends ScalaModule with ZioHttpGenModule { self =>

  object openapi extends OpenAPIModule

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

```sh
./mill __.reformat + __.test + __.publishLocal
```

All contributions are welcome!
