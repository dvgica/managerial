# Managerial
![Maven](https://img.shields.io/maven-central/v/ca.dvgi/managerial_3?color=blue) ![CI](https://img.shields.io/github/workflow/status/dvgica/managerial/Continuous%20Integration)

Managerial is a small, dependency-free library providing `Managed`, a composable type for setting up and tearing down `Resource`s.

- [Motivation](#motivation)
- [Installation](#installation)
- [Usage](#usage)
  * [Basic Automatic Resource Management Example](#basic-automatic-resource-management-example)
  * [Composed Resources Example](#composed-resources-example)
- [Related Libraries](#related-libraries)
  * [Twitter Util's `Managed`](#twitter-util-s--managed-)
  * [Scala ARM](#scala-arm)
  * [Scala Stdlib `Using`](#scala-stdlib--using-)
  * [cats-effect `Resource`](#cats-effect--resource-)
- [Contributing](#contributing)

## Motivation

This library can aid with basic automatic resource management, that is, automatically closing or tearing down resources once they have been used, regardless of exceptions. In this way, it is similar to Scala's `Using`, Java's `try-with-resources`, etc. This is not very exciting, but perhaps useful in some circumstances.

Where Managerial really shines is constructing a program in your `main` method. Building a program (especially with manual dependency injection) often requires setup and teardown of various resources which may depend on each other. It's also often useful to have side-effects (e.g. logging) interspersed in this setup and teardown. Doing this construction manually is tedious and error-prone; in particular, developers can easily forget to tear down a resource, or may tear down resources in a different order than they were setup. Managerial makes these errors impossible by allowing for composition of `Managed` resources in a monadic style (i.e. for comprehensions, `flatMap`, `map`).

None of the ideas in the lib are particularly novel (see [Related Libraries](#related-libraries)). But, some may find this particular combination of features enticing.

## Installation

Managerial is available on Maven Central for Scala 2.12, 2.13, and 3.0.

Add the following dependency description to your build.sbt:

`"ca.dvgi" %% "managerial" % "<latest>"`

## Usage

`Managed[T]` instances are created via `Managed#apply`, `Managed#setup`, or `Managed#from`. Additionally, arbitrary actions can be made into `Managed[Unit]` instances via various `Managed#eval` methods.

Multiple `Managed` instances are composed or stacked via `flatMap`, generally with a for comprehension.

Once the `Managed` stack is composed, the underlying resources are built and used with `use` or `useUntilShutdown`. Setup occurs in the order of the for comprehension or `flatMap`s, and teardown happens in the reverse order.

Exception behavior is as follows:
- Exceptions during setup are thrown after already-built resources are torn down
- Exceptions during usage are thrown after resources are torn down
- Exceptions during teardown are thrown, but only after teardown is called on every resource. If an exception was thrown during usage, the teardown exceptions are added as suppressed exceptions on the usage exception.

For more details, see the Scaladocs.

### Basic Automatic Resource Management Example

`file.txt` will be automatically closed after `use`, regardless of exceptions thrown.
``` scala
import ca.dvgi.managerial._
val fileContents = Managed.from(scala.io.Source.fromFile("file.txt")).use(_.mkString)
```

### Composed Resources Example

This is a more full-featured example, showing Managerial's typical use-case.

``` scala
import ca.dvgi.managerial._

object Main extends App {

  val server = for {
    // create a Managed[Unit] for side effects
    _ <- Managed.eval(println("Starting setup..."))(println("Finished teardown"))

    // create Managed[Settings] that doesn't require teardown
    settings <- Managed.setup(Settings(8080, 7070))

    // create Managed[HealthCheckServer], which requires teardown
    healthCheckServer <- Managed(new HealthCheckServer(settings))(_.stop())

    // Managed#from expects a type class instance for Teardown[T], instead of having teardown specified explicitly.
    // ca.dvgi.managerial provides Teardown[AutoCloseable].
    apiServer <- Managed.from(new ApiServer(settings))

    // once the ApiServer is started, the HealthCheckServer can show it's ready
    _ <- Managed.eval(healthCheckServer.markReady())(healthCheckServer.markUnready())

    // evalSetup and evalTeardown allow for side-effects during only setup or only teardown
    _ <- Managed.evalSetup(println("Startup is finished!"))
  } yield apiServer

  // builds the Managed stack and registers a JVM shutdown hook to do automatic teardown
  server.useUntilShutdown()
}

case class Settings(healthCheckPort: Int, apiPort: Int)

class HealthCheckServer(settings: Settings) {
  println("Started HealthCheckServer")
  def stop(): Unit = {
    println("Stopped HealthCheckServer")
  }
  def markReady(): Unit = println("Marked HealthCheckServer Ready")
  def markUnready(): Unit = println("Marked HealthCheckServer Unready")
}

class ApiServer(settings: Settings) extends AutoCloseable {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration.Duration
  import scala.concurrent.Await
  import scala.concurrent.Future
  val fakeServer = new Thread {
    override def run: Unit = {
      Await.ready(Future.never, Duration.Inf)
    }
  }
  fakeServer.start()
  println("Started ApiServer")
  def close(): Unit = {
    println("Stopped ApiServer")
  }
}

```

When run, this program outputs:

```
Starting setup...
Started HealthCheckServer
Started ApiServer
Marked HealthCheckServer Ready
Startup is finished!
^C
Marked HealthCheckServer Unready
Stopped ApiServer
Stopped HealthCheckServer
Finished teardown
```


## Related Libraries

### Twitter Util's `Managed`

Managerial is very similar in style to Twitter Util's [`Managed`](https://twitter.github.io/util/docs/com/twitter/util/Managed.html), and borrows a lot of code from it.

Unlike the Twitter Util library, Managerial:
- does not have any dependencies apart from the Scala stdlib
- does not allow for asynchronous resource disposal/release
- attempts to expose a better API for constructing instances of `Managed`
- works with `AutoCloseable` out-of-the-box

### Scala ARM

Managerial is also quite similar to [Scala ARM](https://github.com/jsuereth/scala-arm).

Unlike Scala ARM, Managerial:
- is (officially) published for Scala 2.13 and 3
- lacks some of the "fancy" features, like Delimited Continuation style, reflection-based teardown, or JTA transaction support

### Scala Stdlib `Using`

Unlike Scala's [`Using`](https://www.scala-lang.org/api/2.13.3/scala/util/Using$.html), Managerial:
- is available for Scala 2.12
- can be used in `for` comprehensions, similar to Twitter Util's `Managed`
- does not require constructing `Releaseable` type class instances for each resource that is not `AutoCloseable`

### cats-effect `Resource`

Unlike cats-effect's [`Resource`](https://typelevel.org/cats-effect/docs/std/resource), Managerial:
- does not have any dependencies apart from the Scala stdlib
- does not abstract over effects (you may actually want that, in which case `cats-effect` is a better choice)

## Contributing

Contributions in the form of Issues and PRs are welcome.
