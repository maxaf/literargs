package literargs

import cats.{ Id, Monad, Functor }

object `package` {
  implicit class Args(ctx: StringContext) {
    class Args {
      def unapply(unapplied: Array[String]): Any = macro ArgsMacros.unapplyImpl
    }
    object args extends Args
    object argsd extends Args
  }

  private[literargs] def using[A](factory: => A)(op: A => Any): A = {
    val a = factory
    op(a)
    a
  }
}
