package literargs

import scala.collection.immutable.StringOps
import cats.data.NonEmptyList

object OptionParsers {
  sealed trait Arg
  case class BooleanArgs(matched: NonEmptyList[OptName]) extends Arg
  case class ValueArg(matched: OptName, value: String) extends Arg
  case class GarbageArg(garbage: String) extends Arg
}

class OptionParsers(val opts: NonEmptyList[Opt]) extends CommonParsers {
  val shortBooleanOpts = opts
    .toList
    .collect { case Opt(OptName(short, _), BooleanHole) => short }
  val longBooleanOpts = opts
    .toList
    .collect { case Opt(OptName(_, Some(long)), BooleanHole) => long }
  import OptionParsers._

  private val NullByte = s"${Char.MinValue}"
  private val Value = s"[^${NullByte}]+".r

  def by[N](f: OptName => N, n: N): Option[Opt] =
    opts.find(opt => f(opt.name) == n)

  def shortBooleanArgs: Option[Parser[BooleanArgs]] = {
    shortBooleanOpts match {
      case Nil => None
      case letters =>
        Some {
          "-" ~> s"[${letters.mkString("")}]+".r ^^ {
            shorts =>
              val Some(matched) = NonEmptyList.fromList(
                new StringOps(shorts).flatMap(by(_.short, _)).map(_.name).toList)
              BooleanArgs(matched)
          }
        }
    }
  }

  def longBooleanArgs: Option[Parser[BooleanArgs]] = {
    longBooleanOpts match {
      case Nil => None
      case longNames => Some {
        "--" ~> longNames.drop(1).foldLeft(longNames.head.r: Parser[String])(_ | _) ^^ {
          long =>
            val Some(matched) = NonEmptyList.fromList(
              by(_.long, Some(long)).map(_.name).toList)
            BooleanArgs(matched)
        }
      }
    }
  }

  def booleanArgs: Option[Parser[BooleanArgs]] =
    shortBooleanArgs.flatMap(short => longBooleanArgs.map(short | _)) orElse shortBooleanArgs orElse longBooleanArgs

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

  def anyArg: Option[Parser[Arg]] =
    booleanArgs
      .flatMap(bool => valueArgs.map(bool | _))
      .orElse(valueArgs)

  def garbageArg: Parser[Arg] = s"[^${NullByte}]+".r ^^ { GarbageArg(_) }

  def args: Parser[List[Arg]] =
    repsep(anyArg.map(_ | garbageArg).getOrElse(garbageArg), NullByte)

  def join(argv: Array[String]) = argv.mkString(NullByte)
  def parse_!(argv: Array[String]) = {
    parseCommon(_.args)(join(argv))
  }
}
