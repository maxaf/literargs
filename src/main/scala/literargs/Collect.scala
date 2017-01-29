package literargs

import cats.{ Id, Monad, MonadFilter, Semigroup }
import cats.data.NonEmptyList
import cats.syntax.either._
import cats.syntax.semigroup._
import OptionParsers._

sealed abstract class Collect[M[_]] {
  protected def get(cmd: List[Arg], opt: Opt): List[String] =
    cmd.collect { case ValueArg(name, value) if name == opt.name => value }
  def collect(cmd: List[Arg], opt: Opt): Either[Throwable, M[String]]
}

object Collect {
  implicit def collectM[M[_]](implicit M: MonadFilter[M], SM: Semigroup[M[String]]) =
    new Collect[M] {
      def collect(cmd: List[Arg], opt: Opt) =
        Right {
          if (cmd.collect { case ValueArg(name, _) if name == opt.name => name }.nonEmpty)
            M.filter(
              get(cmd, opt)
                .map(M.pure(_))
                .foldLeft(M.empty[String])(_ |+| _)
            )(_ != null)
          else M.empty
        }
    }

  implicit def collectId(implicit CO: Collect[Option], M: Monad[Id]) =
    new Collect[Id] {
      def collect(cmd: List[Arg], opt: Opt) = CO.collect(cmd, opt).flatMap(
        _
          .map(value => Right(M.pure(value)))
          .getOrElse(Left(new Exception(s"missing required unary $opt in $cmd")))
      )
    }

  implicit def collectNel(implicit CL: Collect[List], M: Monad[NonEmptyList]) =
    new Collect[NonEmptyList] {
      def collect(cmd: List[Arg], opt: Opt) = CL.collect(cmd, opt).flatMap(
        NonEmptyList
          .fromList(_)
          .map(Right(_))
          .getOrElse(Left(new Exception(s"missing required N-ary $opt in $cmd")))
      )
    }
}
