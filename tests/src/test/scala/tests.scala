package literargs

object multiple {
  def main(argv: Array[String]) {
    val args"""
    -s|--size <${ size }> -m [$mode] -f|--flavor [$flavor]
    -o|--foo <$foo> -b|--bar [$bar]
    """ = argv
    println(s"size = $size, mode = $mode, flavor = $flavor, foo = $foo, bar = $bar")
  }
}

object single {
  val args"-a|--aaa <$a:Int> -b [$b:Long]" = Array("-b", "1")
}
