package literargs

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import scala.util.parsing.combinator._
import scala.util.parsing.input.Positional
import org.apache.commons.cli.{ Option => COption, CommandLine }
import cats.{ Id, Monad, Functor }
import cats.data.Xor

object `package` {
  implicit class Args(ctx: StringContext) {
    object args {
      def unapply(unapplied: Array[String]): Any = macro ArgsMacros.unapplyImpl
    }
  }
  def using[A](factory: => A)(op: A => Any): A = {
    val a = factory
    op(a)
    a
  }
}

case class Name(short: Char, long: Option[String]) {
  val repr = long.getOrElse(s"$short")
  val description = s"-$short"
  val option = long
    .map(new COption(s"$short", _, true, description))
    .getOrElse(new COption(s"$short", true, description))
}
case class Hole(required: Boolean, private[literargs] val ascription: Option[String])
case class Opt(name: Name, hole: Hole) extends Positional {
  val option = using(name.option)(_.setRequired(hole.required))
}

sealed abstract class Purify[M[_]](protected implicit val M: Monad[M]) {
  def purify[A](a: A): M[A]
}

object Purify {
  implicit object PurifyId extends Purify[Id] {
    def purify[A](a: A) = M.pure(a)
  }
  implicit def PurifyOption(implicit M: Monad[Option]) = new Purify[Option] {
    def purify[A](a: A) = M.flatMap(M.pure(a))(Option(_))
  }
}

sealed abstract class Extractor[F[_], T](protected implicit val F: Functor[F]) {
  def extract(raw: F[String]): F[T]
}

object Extractor {
  implicit def ExtractString[F[_]: Functor] = new Extractor[F, String] {
    def extract(raw: F[String]) = F.map(raw)(identity)
  }
}

abstract class Argument[M[_], T](val name: String, val opt: Opt)(implicit cmd: CommandLine, P: Purify[M], E: Extractor[M, T]) {
  private val raw = cmd.getOptionValue(name)
  val string = P.purify(raw)
  val value = E.extract(string)
}

class ArgsMacros(val c: whitebox.Context) extends Parsing {
  import c.universe._

  implicit class Possition(pos: Position) {
    def move(offset: Int) = pos.focus.withPoint(pos.focus.point + offset)
  }

  private class OptPlus(opt: Opt, idx: Int) {
    private val Opt(name, Hole(required, ascription)) = opt
    val ident = TermName(s"`${name.repr}`")
    val tpe = ascription.map(t => tq"${TypeName(t)}").getOrElse(tq"String")
    val replica = q"""
      Opt(
        name = Name(
          short = ${Literal(Constant(name.short))},
          long = ${name.long.map(l => q"Some(${Literal(Constant(l))})").getOrElse(q"None")}),
        hole = Hole(
          required = ${Literal(Constant(required))},
          ascription = None))
    """
    val monad = if (required) tq"cats.Id" else tq"Option"
    val argument = q"""
      object $ident
      extends Argument[$monad, $tpe](
        ${Literal(Constant(name.repr))},
        opts.$ident
      )
    """
    val ordinal = TermName(s"_${idx + 1}")
    val accessor = q"""def $ordinal: Argument[$monad, $tpe] = $ident"""
  }

  def unapplyImpl(unapplied: c.Expr[Array[String]]) = {
    val Select(Apply(_, List(Apply(_, parts))), _) = c.prefix.tree
    val text = parts.map { case Literal(Constant(x: String)) => x }.mkString("")

    val Right(opts) = (new Parser).parse_!(text).right.map(_.zipWithIndex.map {
      case (opt, idx) => new OptPlus(opt, idx)
    })

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
    println(showCode(unapply))
    unapply
  }
}

trait Parsing {
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
