package ca.dvgi.managerial

import scala.collection.BuildFrom

trait CompatibleManagedCompanionOps extends ManagedCompanionOps {

  /** Transforms an IterableOnce[Managed[A]] into a Managed[IterableOnce[A]]. Useful for reducing
    * many Manageds into a single Managed.
    */
  def sequence[A, CC[X] <: IterableOnce[X], To](
      in: CC[Managed[A]]
  )(implicit bf: BuildFrom[CC[Managed[A]], A, To]): Managed[To] = {
    in.iterator
      .foldLeft(const(bf.newBuilder(in))) { (result, ma) =>
        result.flatMap { cca =>
          ma.map { a =>
            cca += a
          }
        }
      }
      .map(_.result())
  }
}
