package literargs

import cats.implicits._

object multiple {
  def main(argv: Array[String]) {
    val args"""
    -s|--size <${ size }> -m [$mode] -f|--flavor [$flavor]
    -o|--foo <$foo> -b|--bar [$bar]
    """ = argv
    println(s"""size = ${size.value}, mode = ${mode.value}, flavor = ${flavor.value}, foo = ${foo.value}, bar = ${bar.value}""")
  }
}

object single {
  val args"-a|--aaa <$a> -b [$b]" = Array("-a", "1")
}
