
package literargs

import scala.util.parsing.combinator._

trait CommonParsers extends RegexParsers {
  self =>

  override def skipWhitespace = false

  def maybeWhitespace = opt("""\s+""".r)
  def maybeNewline = opt(sys.props("line.separator"))

  def longName = "--" ~> "[a-zA-Z][a-zA-Z0-9_-]+".r
  def shortName = "-" ~> "[a-zA-Z0-9]".r

  def optionEnd = "[ =]?".r

  def parseCommon[A](f: self.type => Parser[A])(text: String): Either[Either[Error, Failure], A] =
    parseAll(f(self), text) match {
      case result @ Success(good, x) => Right(good)
      case fail @ Failure(_, _) => Left(Right(fail))
      case err @ Error(_, _) => Left(Left(err))
    }
}
