package ca.dvgi.managerial

/** A wrapper for arbitrary resources. Not generally used directly, see [[Managed$]] instead.
  */
trait Resource[+T] {
  def get: T
  def teardown(): Unit
}

object Resource {
  def const[T](t: T): Resource[T] = new Resource[T] {
    def get = t
    def teardown() = ()
  }
}
