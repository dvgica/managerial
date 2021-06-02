package ca.dvgi.managerial

/** An exception wrapping two exceptions occuring during teardown. The wrapped exceptions may
  * also be [[TeardownDoubleException]]s to support an arbitrary number of faiures.
  */
class TeardownDoubleException(cause1: Throwable, cause2: Throwable) extends Exception {
  override def getStackTrace: Array[StackTraceElement] = cause1.getStackTrace
  override def getMessage: String =
    "Double exception while tearing down composite resource: %s, %s".format(
      cause1.getMessage,
      cause2.getMessage
    )
}
