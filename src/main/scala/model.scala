package literargs

import scala.util.parsing.input.Positional
import cats.Id
import org.apache.commons.cli.{ Option => COption, CommandLine }

case class Usage(name: String, opts: List[Opt])

trait Opt {
  def name: OptName
  private[literargs] def hole: Hole
  private[literargs] def option: COption
}

case class OptName(short: Char, long: Option[String]) {
  val repr = long.getOrElse(s"$short")
  val description = s"-$short"
  def builder() = using(COption.builder(short.toString)) {
    builder =>
      long.foreach(builder.longOpt(_))
  }
}

sealed trait Hole {
  def required: Boolean
  private[literargs] def ascription: Option[String]

  def option(name: OptName): COption
}

object Hole {
  def unapply(hole: Hole): Option[(Boolean, Option[String])] =
    Some(hole.required -> hole.ascription)
}

case class ValueHole(required: Boolean, private[literargs] val ascription: Option[String]) extends Hole {
  def option(name: OptName): COption =
    using(name.builder())(_.hasArg(true).required(required)).build()
}

case object BooleanHole extends Hole {
  def required = false
  private[literargs] def ascription = None
  def option(name: OptName): COption =
    using(name.builder())(_.hasArg(false).required(false)).build()
}

case class ParsedOpt(name: OptName, private[literargs] val hole: Hole) extends Opt with Positional {
  val option = hole.option(name)
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
