package ist.dvg.managerial

class ResourceTest extends munit.FunSuite {
  test("A const Resource does very little") {
    val i = 42
    val resource = Resource.const(i)
    assertEquals(resource.get, i)
    assertEquals(resource.teardown(), ())
  }
}
