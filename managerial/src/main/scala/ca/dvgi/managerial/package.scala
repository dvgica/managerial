package ca.dvgi

/** Managerial is a library for managing resource lifecycle monadically.
  *
  * The main entry points to the library are found in [[Managed$]].
  *
  * The following is a full-fledged usage example:
  * {{{
  * import ca.dvgi.managerial.Managed
  * import ca.dvgi.managerial._
  *
  * object Main extends App {
  *
  *   val server = for {
  *     // create a Managed[Unit] for side effects
  *     _ <- Managed.eval(println("Starting setup..."))(println("Finished teardown"))
  *
  *     // create Managed[Settings] that doesn't require teardown
  *     settings <- Managed.setup(Settings(8080, 7070))
  *
  *     // create Managed[HealthCheckServer], which requires teardown
  *     healthCheckServer <- Managed(new HealthCheckServer(settings))(_.stop())
  *
  *     // Managed#from expects a type class instance for Teardown[T], instead of having teardown specified explicitly.
  *     // ca.dvgi.managerial provides Teardown[AutoCloseable].
  *     apiServer <- Managed.from(new ApiServer(settings))
  *
  *     // once the ApiServer is started, the HealthCheckServer can show it's ready
  *     _ <- Managed.eval(healthCheckServer.markReady())(healthCheckServer.markUnready())
  *
  *     // evalSetup and evalTeardown allow for side-effects during only setup or only teardown
  *     _ <- Managed.evalSetup(println("Startup is finished!"))
  *   } yield apiServer
  *
  *   // builds the Managed stack and registers a JVM shutdown hook to do automatic teardown
  *   server.useUntilShutdown()
  * }
  *
  * case class Settings(healthCheckPort: Int, apiPort: Int)
  *
  * class HealthCheckServer(settings: Settings) {
  *   println("Started HealthCheckServer")
  *   def stop(): Unit = {
  *     println("Stopped HealthCheckServer")
  *   }
  *   def markReady(): Unit = println("Marked HealthCheckServer Ready")
  *   def markUnready(): Unit = println("Marked HealthCheckServer Unready")
  * }
  *
  * class ApiServer(settings: Settings) extends AutoCloseable {
  *   import scala.concurrent.ExecutionContext.Implicits.global
  *   import scala.concurrent.duration.Duration
  *   import scala.concurrent.Await
  *   import scala.concurrent.Future
  *   val fakeServer = new Thread {
  *     override def run: Unit = {
  *       Await.ready(Future.never, Duration.Inf)
  *     }
  *   }
  *   fakeServer.start()
  *   println("Started ApiServer")
  *   def close(): Unit = {
  *     println("Stopped ApiServer")
  *   }
  * }
  * }}}
  */
package object managerial {

  /** A type class instance describing how to teardown a java.lang.AutoCloseable
    */
  implicit val autoCloseableTeardown: Teardown[AutoCloseable] = new Teardown[AutoCloseable] {
    def teardown(ac: AutoCloseable): Unit = ac.close()
  }
}
