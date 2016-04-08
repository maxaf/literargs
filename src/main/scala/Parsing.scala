package literargs

import scala.util.parsing.combinator._
import scala.util.parsing.input.Positional

trait Parsing {
  self: ArgsMacros =>

  class Parser extends RegexParsers {
    override def skipWhitespace = false

    def optionEnd = "[ =]?".r

    def longName = "--" ~> "[a-zA-Z][a-zA-Z0-9_-]+".r
    def shortName = "-" ~> "[a-zA-Z0-9]".r

    def name = (shortName ~ opt('|' ~> longName)) <~ optionEnd ^^ {
      case short ~ long => OptName(short.head, long)
    }

    def unary = "".r ^^ { _ => Unary(_) }
    def n_ary = "\\.\\.".r ^^ { _ => N_ary(_) }
    def arity = n_ary | unary

    def valueHole(reqd: Boolean, open: String, close: String): Parser[Hole] =
      open ~> (arity ~ opt(":" ~> "[a-zA-Z]+".r)) <~ close ^^ { case ar ~ as => ValueHole(ar(reqd), as) }
    def requiredValue = valueHole(reqd = true, open = "<", close = ">")
    def optionalValue = valueHole(reqd = false, open = "[", close = "]")
    def booleanHole = maybeWhitespace ^^ { _ => BooleanHole }

    def option = positioned(name ~ (requiredValue | optionalValue | booleanHole) ^^ {
      case name ~ required => Opt(name, required)
    })

    def maybeWhitespace = opt("""\s+""".r)
    def maybeNewline = opt(sys.props("line.separator"))

    def line = maybeWhitespace ~ rep1sep(option, " ") ~ maybeWhitespace ^^ {
      case _ ~ line ~ _ => line
    }

    def lines = repsep(line, maybeNewline)

    def usage = maybeNewline ~> maybeWhitespace ~> "Usage: " ~> "[a-zA-Z0-9_-]+".r ~ lines ^^ {
      case name ~ opts => Usage(name, opts.flatten)
    }

    def parse_!(text: String): Either[Either[Error, Failure], Usage] =
      parseAll(usage, text) match {
        case result @ Success(good, x) => Right(good)
        case fail @ Failure(_, _) => Left(Right(fail))
        case err @ Error(_, _) => Left(Left(err))
      }
  }
}
