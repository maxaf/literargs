# Stage 1: Making sense of the language

```tut:silent
// we're going to need the runtime universe again
import scala.reflect.runtime.universe._
```

Let's expand our desired syntax a little, in order to give
ourselves some wiggle room in experimenting on the language:

```scala
val args"""
  -l           [$login_name]  // optional argument
  -p|--port    <$port>        // required argument
  -L           [..$forward]   // optional multi-argument (List)
  -R           <..$reverse>   // required multi-argument (NonEmptyList)
  -v|--verbose $verbose       // boolean argument (true if present)
""" = argv
```

Inside our macro, the above syntax would take a form similar
to:

```scala
List(
  "\n      -l           [",
  "]\n      -p|--port    <",
  ">\n      -L           [..",
  "]\n      -R     <..",
  ">\n      -v|--verbose ",
  "\n    "
)
```

In order to turn this bunch of strings into something useful,
we need to parse the text. For this, we need two things: **a
data model** and **a parser**.

## The data model

Before any parsing can take place, we need to whip up an approximate data model
into which we will parse the text. This isn't too difficult.

Every option will have two names:
- a mandatory 1-character short name (i.e. -v)
- an optional long name (i.e. --verbose)
```scala
case class OptName(
  short: Char,
  long: Option[String] = None
)
```

Every option has a name, and a "hole" in the DSL text, which is where the
symbolic binding was removed by the Scala compiler.
```scala
case class Opt(
  name: OptName,
  hole: Hole
)
```

We know that the "hole" can be either unary (representing a single value) or
N-ary (representing multiple values). The values can also be required or
optional.
```scala
sealed trait Arity
case class Unary(required: Boolean) extends Arity
case class N_ary(required: Boolean) extends Arity
```

Finally, the "hole" itself, can be:
- something asking for a value w/defined arity
- or a simple boolean flag
```scala
sealed trait Hole
case class ValueHole(arity: Arity) extends Hole
case object BooleanHole extends Hole
```

## The parser

Armed with a destination data model and some example DSL text, we're now ready
to make a parser. The simplest possible option is Scala's own
`scala-parser-combinators` library, which allows us to write code like this:

```scala
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
}
```

## The parsed result

Referring back to our DSL text...

```scala
val args"""
  -l           [$login_name]
  -p|--port    <$port>
  -L           [..$forward]
  -R           <..$reverse>
  -v|--verbose $verbose
""" = argv
```

We end up with the following parsed options:

```scala
List(
  Opt(OptName(l,None),          ValueHole(Unary(false)))
  Opt(OptName(p,Some(port)),    ValueHole(Unary(true)))
  Opt(OptName(L,None),          ValueHole(N_ary(false)))
  Opt(OptName(R,None),          ValueHole(N_ary(true)))
  Opt(OptName(v,Some(verbose)), BooleanHole)
)
```

Now that we have a clear in-memory representation of our CLI arguments DSL, we
can do some real work in stage 2!
