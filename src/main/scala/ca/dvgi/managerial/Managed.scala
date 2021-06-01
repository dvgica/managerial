package ca.dvgi.managerial

import scala.util.Try
import scala.util.Failure
import scala.util.Success

trait Managed[+T] { selfT =>

  /** Build the Managed stack, get the resulting Resource's T, and pass it to the given function.
    * After the function completes, the resource is torn down.
    */
  def use(f: T => Unit): Unit = {
    val r = this.build()
    try f(r.get)
    finally r.teardown()
  }

  def foreach(f: T => Unit): Unit = use(f)

  /** Build the Managed stack, and register a JVM shutdown hook to tear it down automatically.
    */
  def useUntilShutdown(): Unit = {
    val r = this.build()
    sys.addShutdownHook(r.teardown())
  }

  /** Compose a new Managed instance that depends on `this' managed resource
    */
  def flatMap[U](f: T => Managed[U]): Managed[U] = new Managed[U] {
    def build() = new Resource[U] {
      val t = selfT.build()

      val u =
        try {
          f(t.get).build()
        } catch {
          case e: Exception =>
            t.teardown()
            throw e
        }

      def get = u.get

      def teardown() = {
        Try(u.teardown()) match {
          case Success(_) =>
            t.teardown()
          case Failure(outer) =>
            Try(t.teardown()) match {
              case Success(_)     => throw outer
              case Failure(inner) => throw new TeardownDoubleException(outer, inner)
            }
        }
      }
    }
  }

  def map[U](f: T => U): Managed[U] = flatMap { t => Managed.const(f(t)) }

  /** Builds a Resource from this Managed,
    * including any Managed instances that were previously composed with it
    */
  def build(): Resource[T]
}

object Managed {

  /** Creates a Managed instance from an existing Resource
    */
  def singleton[T](t: Resource[T]): Managed[T] = new Managed[T] {
    def build() = t
  }

  /** Creates a Managed instance that requires neither setup nor teardown
    */
  def const[T](t: T): Managed[T] = singleton(Resource.const(t))

  /** Creates a Managed instance that requires both setup and teardown
    */
  def apply[T](setup: => T)(teardownFunc: T => Unit): Managed[T] =
    new Managed[T] {
      def build() = new Resource[T] {
        val underlying = setup
        def get = underlying
        def teardown() = teardownFunc(underlying)
      }
    }

  /** Creates a Managed instance that requires both setup and teardown.
    * The teardown procedure is provided by an instance of the Teardown type class.
    *
    * A type class instance for AutoCloseable is provided in ca.dvgi.managerial.
    */
  def from[T](setup: => T)(implicit ev: Teardown[T]): Managed[T] =
    apply(setup)(ev.teardown(_))

  /** Creates a Managed instance that requires setup only
    */
  def setup[T](setup: => T): Managed[T] = apply(setup)(_ => ())

  /** Creates a Managed instance for side-effects on teardown only
    */
  def evalTeardown(run: => Unit): Managed[Unit] = apply(())(_ => run)

  /** Creates a Managed instance for side-effects on setup only
    */
  def evalSetup(run: => Unit): Managed[Unit] = apply(run)(_ => ())

  /** Creates a Managed instance for side-effects on both setup and teardown
    */
  def eval(setupRun: => Unit)(teardownRun: => Unit): Managed[Unit] =
    apply(setupRun)(_ => teardownRun)
}

class TeardownDoubleException(cause1: Throwable, cause2: Throwable) extends Exception {
  override def getStackTrace: Array[StackTraceElement] = cause1.getStackTrace
  override def getMessage: String =
    "Double exception while tearing down composite resource: %s, %s".format(
      cause1.getMessage,
      cause2.getMessage
    )
}
