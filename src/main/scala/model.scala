package literargs

import scala.util.parsing.input.Positional
import cats.Id
import org.apache.commons.cli.{ Option => COption, CommandLine }
import scala.reflect.macros.whitebox

case class Usage(name: String, opts: List[Opt])

case class Opt(name: OptName, private[literargs] val hole: Hole) extends Positional {
  val option = hole.option(name)

  private[literargs] class Plus[C <: whitebox.Context](val c: C, idx: Int, val opt: Opt) {
    import c.universe._
    val ident = TermName(s"`${name.repr}`")
    val tpe = hole.ascription.map(t => tq"${TypeName(t)}").getOrElse(tq"String")
    val replica = q"""
      Opt(
        name = OptName(
          short = ${Literal(Constant(name.short))},
          long = ${name.long.map(l => q"Some(${Literal(Constant(l))})").getOrElse(q"None")}),
        hole = ${hole.replicate(c)})
    """
    val monad = if (hole.required) tq"cats.Id" else tq"Option"
    val argument = hole match {
      case ValueHole(_, _) => q"object $ident extends ValueArgument[$monad, $tpe](opts.$ident)"
      case BooleanHole => q"object $ident extends BooleanArgument(opts.$ident)"
    }
    val ordinal = TermName(s"_${idx + 1}")
    val argType = hole match {
      case ValueHole(_, _) => tq"Argument[$monad, $tpe]"
      case BooleanHole => tq"Argument[cats.Id, Boolean]"
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

private[literargs] sealed trait Hole {
  def required: Boolean
  private[literargs] def ascription: Option[String]

  def option(name: OptName): COption

  def replicate(c: whitebox.Context): c.universe.Tree
}

private[literargs] object Hole {
  def unapply(hole: Hole): Option[(Boolean, Option[String])] =
    Some(hole.required -> hole.ascription)
}

case class ValueHole(required: Boolean, private[literargs] val ascription: Option[String]) extends Hole {
  def option(name: OptName): COption =
    using(name.builder())(_.hasArg(true).required(required)).build()
  def replicate(c: whitebox.Context) = {
    import c.universe._
    q"ValueHole(required = ${Literal(Constant(required))}, ascription = None)"
  }
}

case object BooleanHole extends Hole {
  def required = false
  private[literargs] def ascription = None
  def option(name: OptName): COption =
    using(name.builder())(_.hasArg(false).required(false)).build()
  def replicate(c: whitebox.Context) = {
    import c.universe._
    q"BooleanHole"
  }
}

sealed trait Argument[M[_], T] {
  def opt: Opt
  def value: M[T]
}

class ValueArgument[M[_], T](val opt: Opt)(
    implicit
    cmd: CommandLine,
    P: Purify[M],
    E: Extractor[M, T]
) extends Argument[M, T] {
  val value = E.extract(P.purify(cmd.getOptionValue(opt.name.repr)))
}

class BooleanArgument(val opt: Opt)(implicit cmd: CommandLine) extends Argument[Id, Boolean] {
  def value = cmd.hasOption(opt.name.repr)
}
