package literargs

import cats.implicits._

object multiple {
  def main(argv: Array[String]) {
    val args"""
    Usage: multiple -s|--size <${ size }> -m [$mode]
    -f|--flavor [$flavor]
    -o|--foo <$foo> -b|--bar [$bar:Int]
    """ = argv
    println(s"""
      size   = ${size.value}
      mode   = ${mode.value}
      flavor = ${flavor.value}
      foo    = ${foo.value}
      bar    = ${bar.value}
    """)
  }
}

object single {
  val argsd"Usage: single -a <$a>" = Array("-a", "1")
}
