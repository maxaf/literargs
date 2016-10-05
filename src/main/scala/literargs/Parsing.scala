package literargs

import scala.util.parsing.combinator._

trait Parsing {
  class DslParser extends RegexParsers {
    override def skipWhitespace = false

    def maybeWhitespace = opt("""\s+""".r)
    def maybeNewline = opt(sys.props("line.separator"))

    def longName = "--" ~> "[a-zA-Z][a-zA-Z0-9_-]+".r
    def shortName = "-" ~> "[a-zA-Z0-9]".r

    def optionEnd = "[ =]?".r

    def name = (shortName ~ opt('|' ~> longName)) <~ optionEnd ^^ {
      case short ~ long => OptName(short.head, long)
    }

    def unary = "".r ^^ { _ => Unary(_) }
    def n_ary = "\\.\\.".r ^^ { _ => N_ary(_) }
    def arity = n_ary | unary

    def valueHole(reqd: Boolean, open: String, close: String): Parser[Hole] =
      open ~> arity <~ close ^^ { case ar => ValueHole(ar(reqd)) }
    def requiredValue = valueHole(reqd = true, open = "<", close = ">")
    def optionalValue = valueHole(reqd = false, open = "[", close = "]")
    def booleanHole = maybeWhitespace ^^ { _ => BooleanHole }

    def option = positioned(name ~ maybeWhitespace ~ (requiredValue | optionalValue | booleanHole) ^^ {
      case name ~ _ ~ required => Opt(name, required)
    })

    def line = maybeWhitespace ~ rep1sep(option, " ") ~ maybeWhitespace ^^ {
      case _ ~ line ~ _ => line
    }
    def lines = repsep(line, maybeNewline)

    def parseCommon[A](f: this.type => Parser[A])(text: String): Either[Either[Error, Failure], A] =
      parseAll(f(this), text) match {
        case result @ Success(good, x) => Right(good)
        case fail @ Failure(_, _) => Left(Right(fail))
        case err @ Error(_, _) => Left(Left(err))
      }

    def parse_!(text: String) = parseCommon(_.lines)(text).right.map(_.flatten)
  }
}
