package literargs.test

import literargs._
import cats.data.NonEmptyList
import org.specs2._

class OptionParsersSpec extends Specification {
  object ops extends OptionParsers(NonEmptyList.fromListUnsafe(
    Opt.boolean(OptName('a')) ::
      Opt.boolean(OptName('b', long = Some("bbb"))) ::
      Opt.value(OptName('c')) ::
      Opt.value(OptName('d', long = Some("ddd"))) ::
      Nil
  ))

  def is = s2"""
    OptionParsers must:
      Parse empty argv $parsesNothing
      Parse boolean options:
        short-only $parsesBooleanJustShort
        short repr of long/short $parsesBooleanLongShort
        long repr of long/short $parsesBooleanLongLong
      Parse value options:
        short-only $parsesValueJustShort
        short repr of long/short $parsesValueLongShort
        long repr of long/short $parsesValueLongLong
      Parse mix of options regardless of order:
        sets must parse
          $parsesRandomlyOrderedArgv1
          $parsesRandomlyOrderedArgv2
          $parsesRandomlyOrderedArgv3
        order of options should not matter $orderDoesNotMatter
  """

  private def argv(args: String*) = args.toArray

  val parsesNothing = ops.parse_!(argv()) must beRight

  val parsesBooleanJustShort = ops.parse_!(argv("-a")) must beRight
  val parsesBooleanLongShort = ops.parse_!(argv("-b")) must beRight
  val parsesBooleanLongLong = ops.parse_!(argv("--bbb")) must beRight

  val parsesValueJustShort = ops.parse_!(argv("-c", "foo bar")) must beRight
  val parsesValueLongShort = ops.parse_!(argv("-d", "foo bar")) must beRight
  val parsesValueLongLong = ops.parse_!(argv("--ddd", "foo bar")) must beRight

  def random(): Array[String] =
    (new scala.util.Random(System.nanoTime)).shuffle(
      ("-a" :: Nil) ::
        ("-b" :: Nil) ::
        ("-x" :: Nil) ::
        ("--bbb" :: Nil) ::
        ("-c" :: "foo bar" :: Nil) ::
        ("-c" :: "baz quux" :: Nil) ::
        ("-y" :: "yyy" :: Nil) ::
        ("-d" :: "foo bar" :: Nil) ::
        ("-d" :: "baz quux" :: Nil) ::
        ("--zzz" :: "zzz" :: Nil) ::
        ("--ddd" :: "foo bar" :: Nil) ::
        ("--ddd" :: "baz quux" :: Nil) :: Nil
    ).flatten.toArray

  val parsesRandomlyOrderedArgv1 = ops.parse_!(random()) must beRight
  val parsesRandomlyOrderedArgv2 = ops.parse_!(random()) must beRight
  val parsesRandomlyOrderedArgv3 = ops.parse_!(random()) must beRight

  val orderDoesNotMatter =
    List.fill(10)(random())
      .map(ops.parse_!(_))
      .map(_.right.map(_.toSet))
      .toSet.size must_== 1
}
