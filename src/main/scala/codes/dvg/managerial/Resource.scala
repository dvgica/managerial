package ist.dvg.managerial

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
