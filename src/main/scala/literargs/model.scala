package literargs

import scala.util.parsing.input.Positional
import scala.reflect.macros.whitebox

case class OptName(short: Char, long: Option[String] = None)
case class Opt(name: OptName, hole: Hole) extends Positional

sealed trait Arity
case class Unary(required: Boolean) extends Arity
case class N_ary(required: Boolean) extends Arity

sealed trait Hole
case class ValueHole(arity: Arity) extends Hole
case object BooleanHole extends Hole
