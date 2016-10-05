package tests

import literargs._
import cats.Functor
import cats.implicits._
import java.nio.file.{ Path, Paths }

object ssh {
  def main(argv: Array[String]) {
    val args"""
      -l [$login_name]
      -v $verbose
    """ = argv
    println(s"login = ${login_name.value}")
    println(s"verbose = ${verbose.value}")
  }
}
