package literargs

import cats.{ Id, Monad }

sealed abstract class Purify[M[_]](protected implicit val M: Monad[M]) {
  def purify[A](a: A): M[A]
}

object Purify {
  implicit object PurifyId extends Purify[Id] {
    def purify[A](a: A) = M.pure(a)
  }
  implicit def PurifyOption(implicit M: Monad[Option]) = new Purify[Option] {
    def purify[A](a: A) = M.flatMap(M.pure(a))(Option(_))
  }
}
