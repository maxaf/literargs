package literargs

trait OptPlus_ {
  self: ArgsMacros =>

  import c.universe._

  protected class OptPlus(opt: ParsedOpt, idx: Int) extends Opt {
    private val ParsedOpt(_, Hole(required, ascription)) = opt
    private[literargs] def hole = opt.hole
    def name = opt.name
    private[literargs] def option = opt.option
    val ident = TermName(s"`${name.repr}`")
    val tpe = ascription.map(t => tq"${TypeName(t)}").getOrElse(tq"String")
    val replica = q"""
      ParsedOpt(
        name = OptName(
          short = ${Literal(Constant(name.short))},
          long = ${name.long.map(l => q"Some(${Literal(Constant(l))})").getOrElse(q"None")}),
        hole = Hole(
          required = ${Literal(Constant(required))},
          ascription = None))
    """
    val monad = if (required) tq"cats.Id" else tq"Option"
    val argument = q"""
      object $ident
      extends Argument[$monad, $tpe](
        ${Literal(Constant(name.repr))},
        opts.$ident
      )
    """
    val ordinal = TermName(s"_${idx + 1}")
    val accessor = q"""def $ordinal: Argument[$monad, $tpe] = $ident"""
  }
}
