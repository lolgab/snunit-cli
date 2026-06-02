# SNUnit CLI

SNUnit CLI is a CLI for SNUnit.

## Install

Then you can download the `snunit` script from [Github Releases](https://github.com/lolgab/snunit-cli/releases)

Then you need to install the needed tooling with `./snunit install-tools`

## First example

You can create a directory with a scala file containing a SNUnit server

For example: `example/main.scala`

```scala
import snunit.*
@main def run =
  SyncServerBuilder
    .setRequestHandler(_.send(StatusCode.OK, "Hello world!", Headers.empty))
    .build()
    .listen()
```

After that you can run your function with:

```bash
./snunit run --port 8081 example
```

```
> ./snunit run --port 8081 example

Compiling project (Scala 3.1.1, Scala Native)
Compiled project (Scala 3.1.1, Scala Native)
[info] Linking (1560 ms)
[info] Discovered 730 classes and 4137 methods
[info] Optimizing (debug mode) (1518 ms)
[info] Generating intermediate code (1121 ms)
[info] Produced 8 files
[info] Compiling to native code (2471 ms)
[info] Linking native code (immix gc, none lto) (122 ms)
[info] Total (6881 ms)
  ./.snunit/example.out
Waiting for unit to start...
2022/01/29 19:37:49 [warn] 55045#10895105 Unit is running unprivileged, then it cannot use arbitrary user and group.
2022/01/29 19:37:49 [info] 55045#10895105 unit 1.33.0 started
```

Then you can check the server is running with curl:
```bash
curl http://localhost:8081
Hello world!⏎ 
```

## Creating a Docker image

You can create a docker image of your app with:

```bash
./snunit buildDocker --path example --port 8081 --dockerImage my_great_app
```

Then you can run your image with:

```bash
docker run --rm -p 8081:8081 my_great_app
```

And check everything is working:

```bash
curl -X POST -d 'John Doe' http://localhost:8081
Hello John Doe!!!⏎ 
```

## Example using Cask

For example using the [Cask](https://github.com/com-lihaoyi/cask) module: `example/main.scala`

```scala
//> using lib "com.github.lolgab::snunit-cask::0.10.3"

object MinimalApplication extends cask.MainRoutes {
  @cask.get("/")
  def hello() = {
    "Hello World!"
  }

  initialize()
}
```

After that you can run your server with:

```bash
./snunit run --port 8081 example
```

## Passing custom options to scala-cli

You can pass extra options to scala-cli by using the `--scala-cli-args` option.

```
./snunit run example --scala-cli-args '--native-mode release-fast'
```

## Contributing

### Building

You can build a `jar` based executable with:

```bash
scala-cli package snunit-cli -o snunit
```

You can also build a GraalVM native-image with:

```bash
scala-cli package --native-image snunit-cli -o snunit
```

This generates binaries called `snunit`
