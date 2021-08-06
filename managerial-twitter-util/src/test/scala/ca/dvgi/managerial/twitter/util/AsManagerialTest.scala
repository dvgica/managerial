package ca.dvgi.managerial.twitter.util

import ca.dvgi.{managerial => m}
import com.twitter.{util => tu}

class PackageTest extends munit.FunSuite {
  test("A Twitter Util Managed can be converted to a Managerial Managed") {
    val i = 42
    var disposed = false
    val tum = new tu.Managed[Int] {
      def make() = new tu.Disposable[Int] {
        val underlying = i
        def get = underlying
        def dispose(deadline: tu.Time) = {
          disposed = true
          tu.Future.value(())
        }
      }
    }

    val mm = tum.asManagerial
    assert(!disposed)

    val r = mm.build()
    assert(!disposed)

    assertEquals(r.get, i)
    assert(!disposed)

    r.teardown()
    assert(disposed)
  }

  test("A Managerial Managed can be converted to a Twitter Util Managed") {
    val i = 42
    var torndown = false
    val managed = m.Managed(i)(_ => torndown = true)

    val tum = managed.asTwitterUtil
    assert(!torndown)

    val r = tum.make()
    assert(!torndown)

    assertEquals(r.get, i)
    assert(!torndown)

    r.dispose()
    assert(torndown)
  }
}
