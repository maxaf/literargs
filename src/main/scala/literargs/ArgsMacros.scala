package literargs

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

class ArgsMacros(val c: whitebox.Context) extends Parsing {
  import c.universe._
  object liftables extends Liftables[c.type](c)
  import liftables._

  def unapplyImpl(unapplied: c.Expr[Array[String]]) = {
    val Select(Apply(_, List(Apply(_, parts))), _) = c.prefix.tree

    // parse the options into a data structure
    val opts: List[Opt] =
      (new DslParser).parse_!(extractParts(parts)) match {
        case Right(parsed) => parsed
        case Left(err) => c.abort(c.prefix.tree.pos, s"problem: ${err.getClass}: $err")
      }

    val getters = opts.zipWithIndex.map {
      case (opt, idx) =>
        val monad = opt.hole match {
          case ValueHole(Unary(true)) | BooleanHole => tq"cats.Id"
          case ValueHole(Unary(false)) => tq"Option"
          case ValueHole(N_ary(true)) => tq"cats.data.NonEmptyList"
          case ValueHole(N_ary(false)) => tq"List"
        }
        val name = TermName(s"_${idx + 1}")
        opt.hole match {
          case ValueHole(_) =>
            q"def $name: Argument[$monad, String] = new ValueArgument[$monad](opt = $opt)"
          case BooleanHole =>
            q"def $name: Argument[$monad, Boolean] = new BooleanArgument(opt = $opt)"
        }
    }

    // generate the AST
    q"""
      new {
        class Match(argv: Array[String]) {
          def get = this
          ..$getters
          def isEmpty = false
        }
        def unapply(argv: Array[String]) = new Match(argv)
      }.unapply($unapplied)
    """
  }

  def extractParts(trees: List[Tree]): String =
    trees.collect { case Literal(Constant(x: String)) => x }.mkString("")

  def parseParts(text: String) = {
    val Right(parsed) = (new DslParser).parse_!(text)
    parsed
  }
}
