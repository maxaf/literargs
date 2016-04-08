package literargs

import algebra.Semigroup
import cats.{ Id, Monad, MonadFilter }
import cats.data.{ NonEmptyList, Xor }
import cats.syntax.semigroup._
import org.apache.commons.cli.{ Option => COption, CommandLine }

sealed abstract class Collect[M[_]] {
  protected def array(cmd: CommandLine, opt: Opt) =
    Option(cmd.getOptionValues(opt.name.repr)).getOrElse(Array.empty)
  def collect(cmd: CommandLine, opt: Opt): Xor[Throwable, M[String]]
}

object Collect {
  implicit def collectM[M[_]](implicit M: MonadFilter[M], SM: Semigroup[M[String]]) =
    new Collect[M] {
      def collect(cmd: CommandLine, opt: Opt) =
        Xor.right {
          if (cmd.hasOption(opt.name.repr))
            M.filter(
              array(cmd, opt)
                .map(M.pure(_))
                .foldLeft(M.empty[String])(_ |+| _)
            )(_ != null)
          else M.empty
        }
    }

  implicit def collectId(implicit CO: Collect[Option], M: Monad[Id]) =
    new Collect[Id] {
      def collect(cmd: CommandLine, opt: Opt) = CO.collect(cmd, opt).flatMap(
        _
          .map(value => Xor.right(M.pure(value)))
          .getOrElse(Xor.left(new Exception(s"missing required unary $opt in $cmd")))
      )
    }

  implicit def collectNel(implicit CL: Collect[List], M: Monad[NonEmptyList]) =
    new Collect[NonEmptyList] {
      def collect(cmd: CommandLine, opt: Opt) = CL.collect(cmd, opt).flatMap(
        NonEmptyList
          .fromList(_)
          .map(Xor.right(_))
          .getOrElse(Xor.left(new Exception(s"missing required N-ary $opt in $cmd")))
      )
    }
}
