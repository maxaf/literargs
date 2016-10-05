package literargs

import scala.collection.immutable.StringOps
import cats.data.NonEmptyList

object OptionParsers {
  sealed trait Arg
  case class BooleanArgs(matched: NonEmptyList[OptName]) extends Arg
  case class ValueArg(matched: OptName, value: String) extends Arg
  case class GarbageArg(garbage: String) extends Arg
}
