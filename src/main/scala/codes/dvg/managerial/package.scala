package codes.dvg

package object managerial {
  implicit val autoCloseableTeardown: Teardown[AutoCloseable] = new Teardown[AutoCloseable] {
    def teardown(ac: AutoCloseable): Unit = ac.close()
  }
}
