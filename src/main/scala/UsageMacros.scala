package literargs

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import scala.annotation.StaticAnnotation

class usage(debug: Boolean = false) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro UsageMacros.usageImpl
}

class UsageMacros(val c: whitebox.Context) extends Parts with Parsing {
  import c.universe._

  case class ArgsDecl(
      mods: Modifiers,
      tuple: TermName,
      argv: Tree,
      parts: List[Tree],
      patterns: List[Tree],
      names: List[Tree]
  ) {
    def limit(n: Int = 22): List[ArgsDecl] =
      if (n >= patterns.size) this :: Nil
      else {
        val (thisParts, nextParts) = parseParts(extractParts(parts)).map(_.render).splitAt(n)
        def reifyParts(ps: List[(String, String)]) = ps.map { case (l, r) => q"${s"$l $r"}" }
        val (thisPatterns, nextPatterns) = patterns.splitAt(n)
        val (thisNames, nextNames) = names.splitAt(n)
        copy(
          tuple = c.freshName(),
          parts = reifyParts(thisParts),
          patterns = thisPatterns,
          names = thisNames
        ) :: copy(
            tuple = c.freshName(),
            parts = reifyParts(nextParts),
            patterns = nextPatterns,
            names = nextNames
          ).limit(n)
      }
  }

  implicit object ArgsDeclLiftable extends Liftable[ArgsDecl] {
    def apply(decl: ArgsDecl) = {
      import decl._
      val kase = q"StringContext(..$parts).args(..$patterns)"
      q"""
        $mods val $tuple = ($argv: @scala.unchecked) match {
          case $kase => (..$names)
        }
        val ((..$names)) = $tuple
      """
    }
  }

  object ArgsDecl {
    def unapply(tree: Tree): Option[ArgsDecl] = tree match {
      case q"""$mods val $tuple = ($argv: @scala.unchecked) match {
        case ${ CaseDef(
        q"StringContext(..$parts).args(..$patterns)",
        _,
        q"$creator(..$names)") }
      }""" =>
        Some(ArgsDecl(mods, tuple, argv, parts, patterns, names))
      case _ => None
    }
  }

  def usageImpl(annottees: Tree*): Tree = annottees match {
    case q"def $program { ..$body }" :: Nil =>
      val decls = body
        .collect { case ArgsDecl(x) => x }
        .flatMap(_.limit(22))
        .map(ArgsDeclLiftable(_))
        .flatMap(_.children)
      using(q"""
        object $program { ..$decls }
        import $program._
      """)(println)
  }
}
