package literargs

object `package` {
  implicit class Args(ctx: StringContext) {
    class Args {
      def unapply(unapplied: Array[String]): Any = macro ArgsMacros.unapplyImpl
    }
    object args extends Args
    object argsd extends Args
  }
}
