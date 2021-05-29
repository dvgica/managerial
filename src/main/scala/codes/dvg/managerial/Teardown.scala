package codes.dvg.managerial

trait Teardown[-T] {
  def teardown(t: T): Unit
}
