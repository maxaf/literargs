package literargs

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

class ArgsMacros(val c: whitebox.Context) extends Parsing {
  import c.universe._

  def unapplyImpl(unapplied: c.Expr[Array[String]]) = {
    val Select(Apply(_, List(Apply(_, parts))), _) = c.prefix.tree

    val opts: List[Opt] = (new DslParser).parse_!(extractParts(parts)) match {
      case Right(parsed) => parsed
      case Left(err) => c.abort(c.prefix.tree.pos, s"problem: ${err.getClass}: $err")
    }

    sys.error("\n" + parts + "\n" + "\n" + opts.map(_.toString).mkString("\n") + "\n")
  }

  import c.universe._

  def extractParts(trees: List[Tree]): String =
    trees.collect { case Literal(Constant(x: String)) => x }.mkString("")

  def parseParts(text: String) = {
    val Right(parsed) = (new DslParser).parse_!(text)
    parsed
  }
}
