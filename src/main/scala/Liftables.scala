package literargs

import scala.reflect.macros.whitebox

abstract class Liftables[C <: whitebox.Context](val c: C) {
  import c.universe._
  implicit object liftArity extends Liftable[Arity] {
    def apply(arity: Arity) = arity match {
      case Unary(required) => q"Unary($required)"
      case N_ary(required) => q"N_ary($required)"
    }
  }
  implicit object liftHole extends Liftable[Hole] {
    def apply(hole: Hole) = hole match {
      case ValueHole(arity, ascription) => q"ValueHole($arity, $ascription)"
      case BooleanHole => q"BooleanHole"
    }
  }
  implicit object liftOptName extends Liftable[OptName] {
    def apply(name: OptName) = q"OptName(short = ${name.short}, long = ${name.long})"
  }
  implicit object liftOpt extends Liftable[Opt] {
    def apply(opt: Opt) = q"Opt(name = ${opt.name}, hole = ${opt.hole})"
  }
}
