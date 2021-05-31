## Managerial

Managerial is a small, dependency-free library providing `Managed`, a composable type for setting up and tearing down `Resource`s.

### Motivation

Building a program (especially with manual dependency injection) often requires setup and teardown of various resources which may depend on each other. Doing this manually is tedious and error-prone; in particular, developers can easily forget to tear down a resource, or may tear down resources in a different order than they were setup. Managerial makes these errors impossible.

### Installation

Managerial is available for Scala 2.12, 2.13, and 3.0. Add the following dependency description to your build.sbt:

`"ist.dvg" %% "managerial" % "0.1.0"`

### Usage

`Managed[T]` instances are created via `Managed#apply`, `Managed#setup`, or `Managed#from`. Additionally, arbitrary actions can be made into `Managed[Unit]` instances via various `Managed#eval` methods.

Multiple `Managed` instances are composed or stacked via `flatMap`, generally with a for comprehension.

Once the `Managed` stack is composed, the underlying resources are built and used with `use` or `useUntilShutdown`. Setup occurs in the order of the for comprehension or `flatMap`s, and teardown happens in the reverse order.

Exceptions during setup are thrown after already-built resources are torn down. Exceptions during teardown are thrown, but only after teardown is called on every resource.

For more details, see the Scaladocs.

### Example

For the impatient, here's a full-fledged example:

``` scala
import ist.dvg.managerial.Managed
import ist.dvg.managerial._

object Main extends App {

  val server = for {
    // create a Managed[Unit] for side effects
    _ <- Managed.eval(println("Starting setup..."))(println("Finished teardown"))

    // create Managed[Settings] that doesn't require teardown
    settings <- Managed.setup(Settings(8080, 7070))

    // create Managed[HealthCheckServer], which requires teardown
    healthCheckServer <- Managed(new HealthCheckServer(settings))(_.stop())

    // Managed#from expects a type class instance for Teardown[T], instead of having teardown specified explicitly.
    // ist.dvg.managerial provides Teardown[AutoCloseable].
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


### Related Libraries

#### Twitter Util's `Managed`

Managerial is very similar in style to Twitter Util's [`Managed`](https://twitter.github.io/util/docs/com/twitter/util/Managed.html), and borrows a lot of code from it.

Unlike the Twitter Util library, Managerial:
- does not have any dependencies apart from the Scala stdlib
- does not allow for asynchronous resource disposal/release
- attempts to expose a better API for constructing instances of `Managed`
- works with `AutoCloseable` out-of-the-box

#### Scala Stdlib `Using`

Unlike Scala's [`Using`](https://www.scala-lang.org/api/2.13.3/scala/util/Using$.html), Managerial:
- is available for Scala 2.12
- can be used in `for` comprehensions, similar to Twitter Util's `Managed`
- does not require constructing `Releaseable` type class instances for each resource that is not `AutoCloseable`

#### cats-effect `Resource`

Unlike cats-effect's `Resource`, Managerial:
- does not have any dependencies apart from the Scala stdlib
- does not abstract over effects (you may actually want that, in which case `cats-effect` is a better choice)

### Contributing

Contributions in the form of Issues and PRs are welcome.
