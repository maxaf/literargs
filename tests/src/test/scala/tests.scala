package tests

import literargs._
import cats.Functor
import cats.implicits._
import java.nio.file.{ Path, Paths }

object ssh {
  def main(argv: Array[String]) {
    val args"""
      -l           [$login_name]
      -p|--port    <$port>
      -L           [..$forward]
      -R           <..$reverse>
      -v|--verbose $verbose
    """ = argv
    println(s"login = ${login_name.value}")
    println(s"verbose = ${verbose.value}")
  }
}
