package codes.dvg.managerial

class ManagedTest extends munit.FunSuite {
  class TestResource {
    var tornDown = false
    def teardown(): Unit = tornDown = true
  }

  test("Managed#build") {
    val tr = new TestResource
    val m = Managed(tr)(_.teardown())
    val r = m.build()
    assertEquals(r.get, tr)
  }

  test("Managed#use") {
    val tr = new TestResource
    val m = Managed(tr)(_.teardown())

    m.use { r =>
      assertEquals(r, tr)
    }

    assert(tr.tornDown)
  }

  test("Composed Managed setup and teardown in the right order") {
    sealed trait Event
    case class Setup(resourceId: Int) extends Event
    case class Teardown(resourceId: Int) extends Event
    var events = List[Event]()

    class EventedTestResource(id: Int) {
      events = events :+ Setup(id)

      def teardown(): Unit = {
        events = events :+ Teardown(id)
      }
    }

    val m = for {
      m1 <- Managed(new EventedTestResource(1))(_.teardown())
      m2 <- Managed(new EventedTestResource(2))(_.teardown())
    } yield m2

    assertEquals(events, Nil)

    val r = m.build()

    val expectedSetupEvents = List(Setup(1), Setup(2))
    assertEquals(events, expectedSetupEvents)

    r.teardown()

    assertEquals(events, expectedSetupEvents ++ List(Teardown(2), Teardown(1)))
  }

  test("Managed works with AutoCloseable") {
    class AcTestResource extends AutoCloseable {
      var tornDown = false
      def close(): Unit = tornDown = true
    }

    lazy val ac = new AcTestResource
    val m = Managed.from(ac)

    m.use { r =>
      assertEquals(r.tornDown, false)
      assertEquals(r, ac)
    }

    assert(ac.tornDown)
  }

  test("Managed works with user-defined Teardown type class instances") {
    implicit val teardownTestResource = new Teardown[TestResource] {
      def teardown(tr: TestResource): Unit = tr.teardown()
    }

    lazy val tr = new TestResource
    val m = Managed.from(tr)

    m.use { r =>
      assertEquals(r.tornDown, false)
      assertEquals(r, tr)
    }

    assert(tr.tornDown)
  }
}
