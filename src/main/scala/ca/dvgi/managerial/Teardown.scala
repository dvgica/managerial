package ca.dvgi.managerial

/** A type class describing how to teardown a resource
  */
trait Teardown[-T] {
  def teardown(t: T): Unit
}
