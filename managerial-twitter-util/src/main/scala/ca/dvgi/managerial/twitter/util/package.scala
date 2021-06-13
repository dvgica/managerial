package ca.dvgi.managerial.twitter

import ca.dvgi.{managerial => m}
import com.twitter.{util => tu}

package object util {
  implicit class CompatibleManagerialManaged[T](val managed: m.Managed[T]) extends AnyVal {
    def asTwitterUtil: tu.Managed[T] = new tu.Managed[T] {
      def make() = new tu.Disposable[T] {
        val underlying = managed.build()
        def get = underlying.get
        def dispose(deadline: tu.Time) = tu.Future { underlying.teardown() }
      }
    }
  }

  implicit class CompatibleTwitterUtilManaged[T](val managed: tu.Managed[T]) extends AnyVal {
    def asManagerial: m.Managed[T] = new m.Managed[T] {
      def build() = new m.Resource[T] {
        val underlying = managed.make()
        def get = underlying.get
        def teardown() = tu.Await.result(underlying.dispose())
      }
    }
  }
}
