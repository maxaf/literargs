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
        hole = ${replicateHole(hole)})
    """
    val monad = if (required) tq"cats.Id" else tq"Option"
    val argument = opt.hole match {
      case ValueHole(_, _) => q"object $ident extends ValueArgument[$monad, $tpe](opts.$ident)"
      case BooleanHole => q"object $ident extends BooleanArgument(opts.$ident)"
    }
    val ordinal = TermName(s"_${idx + 1}")
    val argType = opt.hole match {
      case ValueHole(_, _) => tq"Argument[$monad, $tpe]"
      case BooleanHole => tq"Argument[cats.Id, Boolean]"
    }
    val accessor = q"""def $ordinal: $argType = $ident"""
  }

  private def replicateHole(hole: Hole): Tree =
    hole match {
      case ValueHole(required, _) =>
        q"ValueHole(required = ${Literal(Constant(required))}, ascription = None)"
      case BooleanHole => q"BooleanHole"
    }
}
