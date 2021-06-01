package ca.dvgi.managerial

trait Teardown[-T] {
  def teardown(t: T): Unit
}
