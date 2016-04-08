package literargs

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

class ArgsMacros(val c: whitebox.Context) extends Parsing {
  import c.universe._

  implicit class MovablePosition(pos: Position) {
    def move(offset: Int) = pos.focus.withPoint(pos.focus.point + offset)
  }

  def unapplyImpl(unapplied: c.Expr[Array[String]]) = {
    val Select(Apply(_, List(Apply(_, parts))), Debug(debug)) = c.prefix.tree
    val text = parts.map { case Literal(Constant(x: String)) => x }.mkString("")

    val Right(Usage(program, parsed)) = (new Parser).parse_!(text)
    val opts = parsed.zipWithIndex.map { case (opt, idx) => opt[c.type](c, idx) }

    val get = opts match {
      case single :: Nil => q"def get: Argument[${single.monad}, ${single.tpe}] = ${single.ident}"
      case _ => q"def get = this"
    }

    val unapply = q"""
    new {
      import org.apache.commons.cli.{Options, DefaultParser}
      class Match(argv: Array[String]) {
        object opts {
          ..${opts.map(opt => q"val ${opt.ident} = ${opt.replica}")}
        }
        implicit val cmd =
          new DefaultParser().parse({
            val os = new Options
            ..${opts.map(opt => q"os.addOption(opts.${opt.ident}.option)")}
            os
          }, argv)
        ..${opts.map(opt => opt.argument)}
        def isEmpty = ${opts.isEmpty}
        $get
        ..${opts.map(opt => opt.accessor)}
      }
      def unapply(argv: Array[String]) = new Match(argv)
    }.unapply($unapplied)
    """
    if (debug) println(showCode(unapply))
    unapply
  }

  private object Debug {
    def unapply(interpolator: Name): Option[Boolean] =
      Some(interpolator match {
        case TermName("argsd") => true
        case _ => false
      })
  }
}
