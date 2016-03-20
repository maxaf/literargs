package literargs

import cats.implicits._

//object multiple {
//  def main(argv: Array[String]) {
//    val args"""
//    -s|--size <${ size }> -m [$mode] -f|--flavor [$flavor]
//    -o|--foo <$foo> -b|--bar [$bar]
//    """ = argv
//    println(s"""size = ${size.string}, mode = ${mode.string}, flavor = ${flavor.string}, foo = ${foo.string}, bar = ${bar.string}""")
//  }
//}

object single {
  val args"-a|--aaa <$a> -b [$b]" = Array("-a", "1")
}
