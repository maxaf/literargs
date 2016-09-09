package literargs

trait Parsing {
  class Parser extends CommonParsers {
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

    def line = maybeWhitespace ~ rep1sep(option, " ") ~ maybeWhitespace ^^ {
      case _ ~ line ~ _ => line
    }
    def lines = repsep(line, maybeNewline)

    def parse_!(text: String) = parseCommon(_.lines)(text).right.map(_.flatten)
  }
}
