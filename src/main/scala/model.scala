package literargs

import scala.util.parsing.input.Positional
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
  val option = long
    .map(new COption(s"$short", _, true, description))
    .getOrElse(new COption(s"$short", true, description))
}
case class Hole(required: Boolean, private[literargs] val ascription: Option[String])
case class ParsedOpt(name: OptName, private[literargs] val hole: Hole) extends Opt with Positional {
  val option = using(name.option)(_.setRequired(hole.required))
}

abstract class Argument[M[_], T](val name: String, val opt: Opt)(implicit cmd: CommandLine, P: Purify[M], E: Extractor[M, T]) {
  private val raw = cmd.getOptionValue(name)
  val string = P.purify(raw)
  val value = E.extract(string)
}
