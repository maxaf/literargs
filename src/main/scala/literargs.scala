package literargs

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import scala.util.parsing.combinator._
import scala.util.parsing.input.Positional

object `package` {
  implicit class Args(ctx: StringContext) {
    object args {
      def unapply(unapplied: Array[String]): Any = macro ArgsMacros.unapplyImpl
    }
  }
}

class ArgsMacros(val c: whitebox.Context) extends Parts {
  import c.universe._

  implicit class Possition(pos: Position) {
    def move(offset: Int) = pos.focus.withPoint(pos.focus.point + offset)
  }

  def unapplyImpl(unapplied: c.Expr[Array[String]]) = {
    val Select(Apply(_, List(Apply(_, parts))), _) = c.prefix.tree
    val Right(opts) = (new Parser).parse_!(parts.map { case Literal(Constant(x: String)) => x }.mkString(""))
    val accessors =
      if (opts.size > 1)
        opts.zipWithIndex.map {
          case (Opt(name, _), idx) =>
            q"""def ${TermName(s"_${idx + 1}")} = ${Literal(Constant(name.repr))}"""
        }
      else Nil
    val get = opts match {
      case Opt(single, _) :: Nil => Literal(Constant(single.repr))
      case _ => q"this"
    }
    val result = q"""
    new {
      def isEmpty = ${opts.isEmpty}
      def get = $get
      ..$accessors
      def unapply(x: Array[String]) = this
    }.unapply($unapplied)
    """
    println(showCode(result))
    result
  }
}

sealed trait Name {
  def repr: String
}
case class LongName(name: String) extends Name { def repr = name }
case class ShortName(name: Char) extends Name { def repr = s"$name" }
case class Opt(name: Name, required: Boolean) extends Positional

trait Parts {
  self: ArgsMacros =>

  class Parser extends RegexParsers {
    override def skipWhitespace = false

    def optionEnd = "[ =]?".r

    def longName = "--" ~> "[a-zA-Z][a-zA-Z0-9_-]+".r <~ optionEnd ^^ { LongName(_) }
    def shortName = "-" ~> "[a-zA-Z0-9]".r <~ optionEnd ^^ { case x => ShortName(x.head) }

    def required = "<>" ^^ { _ => true }
    def optional = "[]" ^^ { _ => false }

    def option = positioned((longName | shortName) ~ (required | optional) ^^ { case name ~ required => Opt(name, required) })

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
