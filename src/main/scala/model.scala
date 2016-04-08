package literargs

import scala.util.parsing.input.Positional
import cats.Id
import cats.data.Xor
import org.apache.commons.cli.{ Option => COption, CommandLine }
import scala.reflect.macros.whitebox

case class Usage(name: String, opts: List[Opt])

case class Opt(name: OptName, private[literargs] val hole: Hole) extends Positional {
  val option = hole.option(name)

  private[literargs] class Plus[C <: whitebox.Context](val c: C, idx: Int, val opt: Opt) {
    import c.universe._
    val ident = TermName(s"`${name.repr}`")
    val ordinal = TermName(s"_${idx + 1}")
    val (argument, argType) = hole match {
      case BooleanHole =>
        q"object $ident extends BooleanArgument(opts.$ident)" -> tq"Argument[cats.Id, Boolean]"
      case ValueHole(arity, ascription) =>
        val tpe = ascription.map(t => tq"${TypeName(t)}").getOrElse(tq"String")
        val monad = arity match {
          case N_ary(required) => if (required) tq"cats.data.NonEmptyList" else tq"List"
          case Unary(required) => if (required) tq"cats.Id" else tq"Option"
        }
        q"object $ident extends ValueArgument[$monad, $tpe](opts.$ident)" -> tq"Argument[$monad, $tpe]"
    }
    val accessor = q"""def $ordinal: $argType = $ident"""
  }

  private[literargs] def apply[C <: whitebox.Context](c: C, idx: Int) = new Plus[C](c, idx, this)
}

case class OptName(short: Char, long: Option[String]) {
  val repr = long.getOrElse(s"$short")
  val description = s"-$short"
  def builder() = using(COption.builder(short.toString)) {
    builder =>
      long.foreach(builder.longOpt(_))
  }
}

sealed trait Arity {
  def matches(cmd: CommandLine, opt: Opt): Boolean = ???
}
case class Unary(required: Boolean) extends Arity
case class N_ary(required: Boolean) extends Arity

private[literargs] sealed trait Hole {
  def option(name: OptName): COption
}

case class ValueHole(arity: Arity, private[literargs] val ascription: Option[String]) extends Hole {
  def option(name: OptName): COption =
    using(name.builder())(_.hasArg(true).required(arity match {
      case Unary(true) => true
      case _ => false
    })).build()
}

case object BooleanHole extends Hole {
  private[literargs] def ascription = None
  def option(name: OptName): COption =
    using(name.builder())(_.hasArg(false).required(false)).build()
}

sealed trait Argument[M[_], T] {
  def opt: Opt
  def value: Xor[Throwable, M[T]]
}

class ValueArgument[M[_], T](val opt: Opt)(
    implicit
    cmd: CommandLine,
    C: Collect[M],
    E: Extractor[M, T]
) extends Argument[M, T] {
  def value = C.collect(cmd, opt).map(E.extract)
}

class BooleanArgument(val opt: Opt)(implicit cmd: CommandLine) extends Argument[Id, Boolean] {
  def value = Xor.right(cmd.hasOption(opt.name.repr))
}
