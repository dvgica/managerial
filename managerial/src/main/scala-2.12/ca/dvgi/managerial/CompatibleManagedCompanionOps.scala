package ca.dvgi.managerial

import scala.collection.generic.CanBuildFrom

trait CompatibleManagedCompanionOps extends ManagedCompanionOps {

  /** Transforms a TraversableOnce[Managed[A]] into a Managed[TraversableOnce[A]]. Useful for
    * reducing many Manageds into a single Managed.
    */
  def sequence[A, CC[X] <: TraversableOnce[X], To](
      in: CC[Managed[A]]
  )(implicit bf: CanBuildFrom[CC[Managed[A]], A, To]): Managed[To] = {
    in.foldLeft(const(bf(in))) { (result, ma) =>
      result.flatMap { cca =>
        ma.map { a =>
          cca += a
        }
      }
    }.map(_.result())
  }
}
