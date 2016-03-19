package literargs

object tests {

  val args"""

    --size <${ size: String }> -m [$mode] --flavor [$flavor]

    --foo <$foo> --bar [$bar]

    """ = ""

  val args"--single <$single>" = ""
}
