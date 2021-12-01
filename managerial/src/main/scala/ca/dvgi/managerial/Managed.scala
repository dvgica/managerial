package ca.dvgi.managerial

import scala.util.Try
import scala.util.Failure
import scala.util.Success

/** An instance of Managed wraps a resource and manages its lifecycle.
  *
  * Managed instances can be composed via [[Managed.flatMap]], generally in a for comprehension.
  *
  * Once composition is complete, the underlying resources can be setup and accessed via
  * [[Managed.use]] or [[Managed.useUntilShutdown]]. Both methods handle tearing down the setup
  * resources once they are no longer needed. Teardown proceeds in the opposite order from setup.
  */
trait Managed[+T] { selfT =>

  /** Build the [[Managed]] stack, get the resulting Resource's T, and pass it to the given
    * function. After the function completes, the resource is torn down.
    */
  def use[R](f: T => R): R = {
    val r = this.build()
    var toThrow: Throwable = null
    try f(r.get)
    catch {
      case t: Throwable =>
        toThrow = t
        null.asInstanceOf[R] // compiler doesn't know that finally will throw
    } finally {
      try {
        r.teardown()
      } catch {
        case t: Throwable =>
          if (toThrow == null) toThrow = t
          else toThrow.addSuppressed(t)
      }
      if (toThrow != null) throw toThrow
    }
  }

  /** Same as [[Managed.use]], but specifically for side-effects (Unit is returned).
    */
  def foreach(f: T => Unit): Unit = use(f)

  /** Build the [[Managed]] stack, and register a JVM shutdown hook to tear it down automatically.
    *
    * Optionally specify `onSetupException` or `onTeardownException` to handle exceptions on setup
    * or teardown. By default, exceptions on setup or teardown are thrown.
    */
  def useUntilShutdown(
      onSetupException: PartialFunction[Throwable, Unit] = { case t: Throwable => throw t },
      onTeardownException: PartialFunction[Throwable, Unit] = { case t: Throwable => throw t }
  ): Unit = {
    var r: Resource[T] = null

    try {
      r = this.build()
    } catch (onSetupException)

    if (r != null) {
      sys.addShutdownHook {
        try r.teardown()
        catch (onTeardownException)
      }
      ()
    }
  }

  /** Compose a new [[Managed]] instance that depends on `this` managed resource
    */
  def flatMap[U](f: T => Managed[U]): Managed[U] = new Managed[U] {
    def build() = new Resource[U] {
      private val t = selfT.build()

      private val u =
        try {
          f(t.get).build()
        } catch {
          case setupThrowable: Throwable =>
            try {
              t.teardown()
            } catch {
              case teardownThrowable: Throwable =>
                setupThrowable.addSuppressed(teardownThrowable)
            }

            throw setupThrowable
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

  /** Builds a new [[Managed]] instance by applying a function to the resource managed by `this`.
    */
  def map[U](f: T => U): Managed[U] = flatMap { t => Managed.const(f(t)) }

  /** Builds a Resource from this Managed, including any [[Managed]] instances that were previously
    * composed with it.
    *
    * If this method is used instead of [[Managed.use]], [[Resource.teardown]] should be invoked
    * explicitly when the resource is no longer needed.
    */
  def build(): Resource[T]
}

/** Provides various methods for creating [[Managed]] instances.
  */
trait ManagedCompanionOps {

  /** Creates a [[Managed]] instance from an existing Resource
    */
  def singleton[T](t: Resource[T]): Managed[T] = new Managed[T] {
    def build() = t
  }

  /** Creates a [[Managed]] instance that requires neither setup nor teardown
    */
  def const[T](t: T): Managed[T] = singleton(Resource.const(t))

  /** Creates a [[Managed]] instance that requires both setup and teardown
    */
  def apply[T](setup: => T)(teardownFunc: T => Unit): Managed[T] =
    new Managed[T] {
      def build() = new Resource[T] {
        val underlying = setup
        def get = underlying
        def teardown() = teardownFunc(underlying)
      }
    }

  /** Creates a [[Managed]] instance that requires both setup and teardown. The teardown procedure
    * is provided by an instance of the [[Teardown]] type class.
    *
    * A type class instance for java.lang.AutoCloseable is provided in [[ca.dvgi.managerial]].
    */
  def from[T](setup: => T)(implicit ev: Teardown[T]): Managed[T] =
    apply(setup)(ev.teardown(_))

  /** Creates a [[Managed]] instance that requires setup only
    */
  def setup[T](setup: => T): Managed[T] = apply(setup)(_ => ())

  /** Creates a [[Managed]] instance for side-effects on teardown only
    */
  def evalTeardown(run: => Unit): Managed[Unit] = apply(())(_ => run)

  /** Creates a [[Managed]] instance for side-effects on setup only
    */
  def evalSetup(run: => Unit): Managed[Unit] = apply(run)(_ => ())

  /** Creates a [[Managed]] instance for side-effects on both setup and teardown
    */
  def eval(setupRun: => Unit)(teardownRun: => Unit): Managed[Unit] =
    apply(setupRun)(_ => teardownRun)

}

object Managed extends CompatibleManagedCompanionOps
