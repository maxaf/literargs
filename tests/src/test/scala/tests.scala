package literargs

import cats.implicits._

object multiple {
  def main(argv: Array[String]) {
    val argsd"""
    Usage: multiple -s|--size <${ size }> -m [$mode]
    -f|--flavor [$flavor]
    -o|--foo <$foo> -b|--bar [$bar:Int]
    -d|--destroy-everything $destroy
    """ = argv
    println(s"""
      size    = ${size.value}
      mode    = ${mode.value}
      flavor  = ${flavor.value}
      foo     = ${foo.value}
      bar     = ${bar.value}
      destroy = ${destroy.value}
    """)
  }
}

object single {
  val args"Usage: single -a <$a>" = Array("-a", "1")
}
