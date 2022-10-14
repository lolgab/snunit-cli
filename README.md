# SNUnit CLI

SNUnit CLI is a CLI for SNUnit.

## Install

You need to install:
- [scala-cli](https://scala-cli.virtuslab.org/).
- [NGINX Unit](https://unit.nginx.org/installation)

Then you can download the `snunit` script from [Github Releases](https://github.com/lolgab/snunit-cli/releases)

## First example

You can create a directory with a scala file containing a function named `handler`

For example: `example/main.scala`

```scala
def handler(name: String): String = s"Hello $name!"
```

After that you can run your function with:

```bash
./snunit run --path example --port 8081
```

```
> ./snunit run --path example --port 8081

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
2022/01/29 19:37:49 [info] 55045#10895105 unit 1.28.0 started
```

Then you can check the server is running with curl:
```bash
curl -X POST -d 'Lorenzo' http://localhost:8081
Hello Lorenzo!!!⏎ 
```

## Running on JVM

snunit-cli allows running on Jvm too to have a faster development cycle.
The Jvm implementation is a thin layer on top of the [Undertow](https://undertow.io/) Java server.

You can run on Jvm using the `runJvm` command:

```bash
$ snunit runJvm --path func.scala --port 9001
feb 10, 2022 11:33:57 AM io.undertow.Undertow start
INFO: starting server: Undertow - 2.2.14.Final
feb 10, 2022 11:33:57 AM org.xnio.Xnio <clinit>
INFO: XNIO version 3.8.4.Final
feb 10, 2022 11:33:57 AM org.xnio.nio.NioXnio <clinit>
INFO: XNIO NIO Implementation Version 3.8.4.Final
feb 10, 2022 11:33:57 AM org.jboss.threads.Version <clinit>
INFO: JBoss Threads version 3.1.0.Final
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

## Writing your own HTTP Server with `--no-runtime`

With the `--no-runtime` flag `snunit-cli` will disable its runtime. So you will need to
create your SNUnit HTTP server manually.

For example using the [Cask](https://github.com/com-lihaoyi/cask) module: `example/main.scala`

```scala
//> using lib "com.github.lolgab::snunit-cask::0.1.1"

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
./snunit run --path example --port 8081 --no-runtime
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
