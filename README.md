# SNUnit CLI

SNUnit CLI is a CLI for SNUnit.

## Building

To build you need to install [scala-cli](https://scala-cli.virtuslab.org/).

```bash
scala-cli package snunit-cli -o snunit
```

This generates a binary called `snunit`

## First example

You need to install [NGINX Unit](https://unit.nginx.org/installation).
Then you can create a directory with a scala file containing a function named `handler`

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
2022/01/29 19:37:49 [info] 55045#10895105 unit 1.26.1 started
```

Then you can check the server is running with curl:
```bash
curl -X POST -d 'Lorenzo' http://localhost:8081
Hello Lorenzo!!!⏎ 
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
