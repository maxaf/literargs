package literargs

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

object `package` {
  implicit class Args(ctx: StringContext) {
    object args {
      def unapply(unapplied: Array[String]): Any = macro ArgsMacros.unapplyImpl
    }
  }
}

class ArgsMacros(val c: whitebox.Context) {
  import c.universe._

  def unapplyImpl(unapplied: c.Expr[Array[String]]) = {
    val Select(Apply(_, List(Apply(_, parts))), _) = c.prefix.tree
    sys.error(s"""
      PARTS = $parts
    """)
  }
}
