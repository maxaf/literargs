package literargs

object multiple {
  def main(argv: Array[String]) {
    val args"""
    --size <${ size: String }> -m [$mode] --flavor [$flavor]
    --foo <$foo> --bar [$bar]
    """ = argv
    println(s"size = $size, mode = $mode, flavor = $flavor, foo = $foo, bar = $bar")
  }
}

object single {
  def main(argv: Array[String]) {
    val args"--single <$single>" = argv
    println(s"single = $single")
  }
}
