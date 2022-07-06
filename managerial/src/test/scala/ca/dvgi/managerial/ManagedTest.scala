package ca.dvgi.managerial

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

  test("Managed.setup only does setup") {
    val tr = new TestResource
    val m = Managed.setup(tr)

    m.use { r =>
      assertEquals(r, tr)
    }

    assert(!tr.tornDown)
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
      _ <- Managed(new EventedTestResource(1))(_.teardown())
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
      assert(!r.tornDown)
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

  test("Managed side-effects with eval") {
    var setup = false
    var teardown = false
    val m = Managed.eval({ setup = true })({ teardown = true })

    assert(!setup)
    assert(!teardown)

    val r = m.build()
    assert(setup)
    assert(!teardown)

    r.teardown()
    assert(setup)
    assert(teardown)
  }

  test("Managed side-effects with evalSetup") {
    var setup = false
    val m = Managed.evalSetup({ setup = true })

    assert(!setup)

    val r = m.build()
    assert(setup)

    r.teardown()
    assert(setup)
  }

  test("Managed side-effects with evalTeardown") {
    var teardown = false
    val m = Managed.evalTeardown({ teardown = true })

    assert(!teardown)

    val r = m.build()
    assert(!teardown)

    r.teardown()
    assert(teardown)
  }

  test("An exception in the Managed setup stack causes previous Manageds to be torn down") {
    val tr = new TestResource
    val testException = new RuntimeException("test exception")
    val m = for {
      _ <- Managed(tr)(_.teardown())
      er <- Managed.evalSetup(throw testException)
    } yield er

    assert(!tr.tornDown)

    interceptMessage[RuntimeException](testException.getMessage) {
      m.build()
    }

    assert(tr.tornDown)
  }

  test(
    "An exception in the Managed teardown stack doesn't stop other Manageds from being torn down"
  ) {
    val tr = new TestResource
    val testException = new RuntimeException("test exception")
    val m = for {
      _ <- Managed(tr)(_.teardown())
      er <- Managed.evalTeardown(throw testException)
    } yield er

    assert(!tr.tornDown)

    val r = m.build()

    assert(!tr.tornDown)

    interceptMessage[RuntimeException](testException.getMessage) {
      r.teardown()
    }

    assert(tr.tornDown)
  }

  test(
    "Multiple exceptions in the Managed teardown stack are surfaced"
  ) {
    val tr1 = new TestResource
    val tr2 = new TestResource

    val testException1 = new RuntimeException("test exception 1")
    val testException2 = new RuntimeException("test exception 2")

    val m = for {
      _ <- Managed(tr1)(_.teardown())
      _ <- Managed.evalTeardown(throw testException1)
      _ <- Managed(tr2)(_.teardown())
      er2 <- Managed.evalTeardown(throw testException2)
    } yield er2

    assert(!tr1.tornDown)
    assert(!tr2.tornDown)

    val r = m.build()

    assert(!tr1.tornDown)
    assert(!tr2.tornDown)

    val expectedMessage =
      s"Double exception while tearing down composite resource: ${testException2.getMessage}, ${testException1.getMessage}"
    interceptMessage[TeardownDoubleException](expectedMessage) {
      r.teardown()
    }

    assert(tr1.tornDown)
    assert(tr2.tornDown)
  }

  test("Managed map returns a new Managed") {
    val tr = new TestResource
    val m = Managed(tr)(_.teardown())

    val i = 42
    val newM = m.map { r =>
      assert(!r.tornDown)
      i
    }
    assert(!tr.tornDown)
    val r = newM.build()
    assert(!tr.tornDown)
    assertEquals(r.get, i)
    r.teardown()
    assert(tr.tornDown)
  }

  test("Exceptions during use trigger automatic teardown") {
    val tr = new TestResource
    val m = Managed(tr)(_.teardown())
    val e = new RuntimeException("test exception")
    interceptMessage[RuntimeException](e.getMessage) {
      m.use(_ => throw e)
    }
    assert(tr.tornDown)
  }

  test("Exceptions during use trigger automatic teardown, suppressing exceptions during teardown") {
    val tr1 = new TestResource
    val eTeardown1 = new RuntimeException("test teardown exception 1")
    val m1 = Managed(tr1)(_ => throw eTeardown1)
    val tr2 = new TestResource
    val eTeardown2 = new RuntimeException("test teardown exception 2")
    val m2 = Managed(tr2)(_ => throw eTeardown2)

    val m = m1.flatMap(_ => m2)

    val e = new RuntimeException("test exception")
    try {
      m.use(_ => throw e)
    } catch {
      case t: RuntimeException =>
        assertEquals(t.getMessage, e.getMessage)
        assertEquals(t.getSuppressed.size, 1)
        t.getSuppressed()(0) match {
          case te: TeardownDoubleException =>
            assertEquals(
              te.getMessage,
              s"Double exception while tearing down composite resource: ${eTeardown2.getMessage}, ${eTeardown1.getMessage}"
            )
          case e => fail("Unexpected exception", e)
        }
      case _: Throwable => fail("Unexpected exception")
    }
    assert(!tr1.tornDown)
    assert(!tr2.tornDown)
  }

  test("Managed#use can return a non-Unit result") {
    val tr = new TestResource
    val i = 42
    val r = Managed(tr)(_.teardown()).use(_ => i)
    assertEquals(r, i)
    assert(tr.tornDown)
  }

  test(
    "An exception in the Managed setup stack, followed by an exception in the teardown stack, surfaces the setup exception with a suppressed teardown exception"
  ) {
    val setupException = new RuntimeException("setup exception")
    val teardownException = new RuntimeException("teardown exception")

    val m = for {
      _ <- Managed.evalTeardown(throw teardownException)
      _ <- Managed.evalSetup(throw setupException)
    } yield ()

    try {
      m.build()
    } catch {
      case t: RuntimeException =>
        assertEquals(t.getMessage, setupException.getMessage)
        assertEquals(t.getSuppressed.size, 1)
        t.getSuppressed()(0) match {
          case te: RuntimeException =>
            assertEquals(
              te.getMessage,
              teardownException.getMessage
            )
          case e => fail("Unexpected exception", e)
        }
      case _: Throwable => fail("Unexpected exception")
    }
  }

  test("Managed.sequence") {
    val tr1 = new TestResource
    val tr2 = new TestResource
    val tr3 = new TestResource

    val trs =
      Set(Managed(tr1)(_.teardown()), Managed(tr2)(_.teardown()), Managed(tr3)(_.teardown()))

    val mtrs = Managed.sequence(trs)

    assert(!tr1.tornDown)
    assert(!tr2.tornDown)
    assert(!tr3.tornDown)

    val r = mtrs.build()

    assert(!tr1.tornDown)
    assert(!tr2.tornDown)
    assert(!tr3.tornDown)
    assertEquals(r.get, Set(tr1, tr2, tr3))

    r.teardown()

    assert(tr1.tornDown)
    assert(tr2.tornDown)
    assert(tr3.tornDown)
  }

  test("Managed.sequence exception in setup") {
    val tr1 = new TestResource
    val e = new RuntimeException("test")

    val trs =
      Set(Managed(tr1)(_.teardown()), Managed.evalSetup(throw e))

    val mtrs = Managed.sequence(trs)

    assert(!tr1.tornDown)

    interceptMessage[RuntimeException](e.getMessage) {
      mtrs.build()
    }

    assert(tr1.tornDown)
  }

  test("Managed.sequence exception in teardown") {
    val tr1 = new TestResource
    val tr2 = new TestResource
    val e = new RuntimeException("test")

    val trs =
      List(Managed(tr1)(_.teardown()), Managed(tr2)(_ => throw e))

    val mtrs = Managed.sequence(trs)

    assert(!tr1.tornDown)

    val r = mtrs.build()
    val expected = List(tr1, tr2)
    assertEquals(r.get, expected)

    interceptMessage[RuntimeException](e.getMessage) {
      r.teardown()
    }

    assert(tr1.tornDown)
  }

  test("Managed#run") {
    val tr = new TestResource
    val m = Managed(tr)(_.teardown())

    assertEquals(m.run(), ())

    assert(tr.tornDown)
  }
}
