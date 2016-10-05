package literargs

import scala.util.parsing.input.Positional
import scala.reflect.macros.whitebox
import cats.Id
import cats.data.Xor
import OptionParsers._

case class OptName(short: Char, long: Option[String] = None)
case class Opt(name: OptName, hole: Hole) extends Positional

sealed trait Arity
case class Unary(required: Boolean) extends Arity
case class N_ary(required: Boolean) extends Arity

sealed trait Hole
case class ValueHole(arity: Arity) extends Hole
case object BooleanHole extends Hole

sealed trait Argument[M[_], T] {
  def opt: Opt
  def value: Xor[Throwable, M[T]]
}

class ValueArgument[M[_]](val opt: Opt) /*(implicit cmd: List[Arg])*/ extends Argument[M, String] {
  def value = ???
}

class BooleanArgument(val opt: Opt) /*(implicit cmd: List[Arg])*/ extends Argument[Id, Boolean] {
  import cats.implicits._
  def value = ???
}
