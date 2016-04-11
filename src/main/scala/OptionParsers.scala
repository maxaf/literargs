package literargs

import scala.collection.immutable.StringOps
import scala.util.parsing.combinator._
import scala.util.parsing.input.Positional
import cats.data.NonEmptyList
import cats.implicits._

object OptionParsers {
  sealed trait Arg
  case class BooleanArgs(matched: NonEmptyList[OptName]) extends Arg
  case class ValueArg(matched: OptName, value: String) extends Arg
  case class GarbageArg(garbage: String) extends Arg
}

class OptionParsers(val opts: NonEmptyList[Opt]) extends CommonParsers {
  import OptionParsers._

  private val NullByte = s"${Char.MinValue}"
  private val Value = s"[^${NullByte}]+".r

  def by[N](f: OptName => N, n: N): Option[Opt] =
    opts.find(opt => f(opt.name) == n)

  def shortBooleanArgs: Parser[BooleanArgs] = {
    val letters = opts
      .toList
      .collect { case Opt(OptName(short, _), BooleanHole) => short }
    "-" ~> s"[${letters.mkString("")}]+".r ^^ {
      shorts =>
        val Some(matched) = NonEmptyList.fromList(
          new StringOps(shorts).flatMap(by(_.short, _)).map(_.name).toList
        )
        BooleanArgs(matched)
    }
  }

  def longBooleanArgs: Option[Parser[BooleanArgs]] = {
    opts.toList.collect {
      case Opt(OptName(_, Some(long)), BooleanHole) => long
    } match {
      case Nil => None
      case longNames => Some {
        "--" ~> longNames.drop(1).foldLeft(longNames.head.r: Parser[String])(_ | _) ^^ {
          long =>
            val Some(matched) = NonEmptyList.fromList(
              by(_.long, Some(long)).map(_.name).toList
            )
            BooleanArgs(matched)
        }
      }
    }
  }

  def booleanArgs: Parser[BooleanArgs] =
    longBooleanArgs.map(shortBooleanArgs | _).getOrElse(shortBooleanArgs)

  def valueArg(opt: Opt): Parser[ValueArg] = {
    val shortName = "-" ~> s"${opt.name.short}".r ^^ { _.head }
    val short = shortName ~> NullByte ~> Value

    val longName = opt.name.long.map(_.r).map("--" ~> _)
    val long = longName.map(_ ~> (NullByte | "=") ~> Value)

    long.map(short | _).getOrElse(short) ^^ { ValueArg(opt.name, _) }
  }

  def valueArgs: Option[Parser[ValueArg]] =
    opts.toList.collect {
      case opt @ Opt(_, ValueHole(_, _)) => valueArg(opt)
    } match {
      case Nil => None
      case parsers => Some(parsers.reduce(_ | _))
    }

  def anyArg: Parser[Arg] = valueArgs match {
    case Some(parsers) => booleanArgs | parsers
    case _ => booleanArgs
  }

  def garbageArg: Parser[Arg] = s"[^${NullByte}]+".r ^^ { GarbageArg(_) }

  def args: Parser[List[Arg]] = repsep(anyArg | garbageArg, NullByte)

  def join(argv: Array[String]) = argv.mkString(NullByte)
  def parse_!(argv: Array[String]) = parseCommon(_.args)(join(argv))
}
