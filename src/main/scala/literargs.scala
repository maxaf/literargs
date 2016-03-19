package literargs

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import scala.util.parsing.combinator._
import scala.util.parsing.input.Positional
import scala.reflect.runtime.universe.{ TypeTag => RuntimeTypeTag }
import org.apache.commons.cli.{ Option => COption, CommandLine }

object `package` {
  implicit class Args(ctx: StringContext) {
    object args {
      def unapply(unapplied: Array[String]): Any = macro ArgsMacros.unapplyImpl
    }
  }
}

case class Name(short: Char, long: Option[String]) {
  val repr = long.getOrElse(s"$short")
  val description = s"-$short"
  val option = long
    .map(new COption(s"$short", _, true, description))
    .getOrElse(new COption(s"$short", true, description))
}
case class Hole(required: Boolean, private[literargs] val tpe: Option[String])
case class Opt(name: Name, hole: Hole) extends Positional {
  val option = {
    val o = name.option
    o.setRequired(hole.required)
    o
  }
}

abstract class Argument[T: RuntimeTypeTag](val name: String)(implicit cmd: CommandLine) {
  def tag = implicitly[RuntimeTypeTag[T]]
  val value = cmd.getOptionValue(name)
}

class ArgsMacros(val c: whitebox.Context) extends Model with Parts {
  import c.universe._

  implicit class Possition(pos: Position) {
    def move(offset: Int) = pos.focus.withPoint(pos.focus.point + offset)
  }

  def unapplyImpl(unapplied: c.Expr[Array[String]]) = {
    val Select(Apply(_, List(Apply(_, parts))), _) = c.prefix.tree
    val text = parts.map { case Literal(Constant(x: String)) => x }.mkString("")
    val Right(opts) = (new Parser).parse_!(text)
    val (arguments, accessorsAndReplicas) = opts.zipWithIndex.map {
      case (Opt(name, hole), idx) =>
        val ident = TermName(s"`${name.repr}`")
        val tpe = holeType(hole)
        val replica = q"""
          Opt(
            name = Name(
              short = ${Literal(Constant(name.short))},
              long = ${name.long.map(l => q"Some(${Literal(Constant(l))})").getOrElse(q"None")}),
            hole = Hole(
              required = ${Literal(Constant(hole.required))},
              tpe = None))
        """
        val argument = q"""
          object $ident
          extends Argument[$tpe](${Literal(Constant(name.repr))})
        """
        val ordinal = TermName(s"_${idx + 1}")
        val accessor = q"""def $ordinal: Argument[$tpe] = $ident"""
        (argument, (accessor, replica))
    }.unzip
    val (accessors, replicas) = accessorsAndReplicas.unzip
    val get = arguments match {
      case q"object $ident extends Argument[$tpe]($name)" :: Nil =>
        q"def get: Argument[$tpe] = $ident"
      case _ => q"def get = this"
    }

    val result = q"""
    new {
      import org.apache.commons.cli.{Options, DefaultParser}
      class Match(argv: Array[String]) {
        implicit val $$commandLine = {
          val $$options = {
            val os = new Options
            ..${replicas.map(replica => q"os.addOption($replica.option)")}
            os
          }
          new DefaultParser().parse($$options, argv)
        }
        ..$arguments
        def isEmpty = ${opts.isEmpty}
        $get
        ..$accessors
      }
      def unapply(argv: Array[String]) = new Match(argv)
    }.unapply($unapplied)
    """
    println(showCode(result))
    result
  }
}

trait Model {
  self: ArgsMacros =>

  import c.universe._

  def holeType(hole: Hole) = {
    val t = hole.tpe.map(t => tq"${TypeName(t)}").getOrElse(tq"String")
    if (hole.required) t else tq"Option[$t]"
  }
}

trait Parts {
  self: ArgsMacros =>

  class Parser extends RegexParsers {
    override def skipWhitespace = false

    def optionEnd = "[ =]?".r

    def longName = "--" ~> "[a-zA-Z][a-zA-Z0-9_-]+".r
    def shortName = "-" ~> "[a-zA-Z0-9]".r

    def name = (shortName ~ opt('|' ~> longName)) <~ optionEnd ^^ {
      case short ~ long => Name(short.head, long)
    }

    def hole(reqd: Boolean, open: String, close: String): Parser[Hole] =
      open ~> opt(":" ~> "[a-zA-Z]+".r) <~ close ^^ { Hole(reqd, _) }
    def required = hole(reqd = true, open = "<", close = ">")
    def optional = hole(reqd = false, open = "[", close = "]")

    def option = positioned(name ~ (required | optional) ^^ { case name ~ required => Opt(name, required) })

    def maybeWhitespace = opt("""\s+""".r)
    def maybeNewline = opt(sys.props("line.separator"))

    def line = maybeWhitespace ~ rep1sep(option, " ") ~ maybeWhitespace ^^ {
      case _ ~ line ~ _ => line
    }

    def lines = repsep(line, maybeNewline)

    def parse_!(text: String): Either[Either[Error, Failure], List[Opt]] =
      parseAll(lines, text) match {
        case result @ Success(good, x) => Right(good.flatten)
        case fail @ Failure(_, _) => Left(Right(fail))
        case err @ Error(_, _) => Left(Left(err))
      }
  }
}
