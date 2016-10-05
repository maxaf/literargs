# Stage 0: Thrust of attack

```tut:silent
// we're going to need the runtime universe again
import scala.reflect.runtime.universe._
```

## Syntax sugar

Before we embark upon a quest to build our own quasiquote-like syntax, let's
first figure out: what does it all compile to?

Funnily enough, quasiquotes come to the rescue even here:

```tut
q"""
  def main(argv: Array[String]) = {
    val args"-f <$$foo> -b [$$bar]" = argv
  }
"""
```

I had to double-escape the `$$` to avoid a quasiquote-inception (you heard it
here first), but the gist is clear:
- The `val` binding compiles down to a pattern match over `argv`.
- Text between double quotes in `args"..."` is broken up into pieces, forming
  holes around name bindings. Neat!
- Once broken up, text is wrapped in a `StringContext` and on it we call
  `.args(foo, bar)`, thus instantiating the `case` and binding `foo` and `bar`,
  which were defined as part of the `args"..."` text.

## The plan

At this stage, we need to set up a skeleton for implementing our macro(s), as
well as take in the idealized `args"..."` syntax (in desugared form) and
extract information from it. We will then use this information to do useful
work.

In order to begin developing macros, I recommend using my `sbt-macro-revolver`
SBT plugin, which provides a shorthand notation for adding macro support to SBT
builds, as well as tooling for rapid iteration during development of macros.

To your `project/plugins.sbt` add:

```scala
resolvers += "bum-snaps" at "http://repo.bumnetworks.com/snapshots/"

addSbtPlugin("com.bumnetworks" % "sbt-macro-revolver" % "0.0.1-SNAPSHOT")
```

Then, as you define a (sub)project in which you'll build the macros, add
`MacroRevolverPlugin.useMacroParadise` to the settings:

```scala
lazy val core = project
  .in(file("."))
  .settings(baseSettings)
  .settings(MacroRevolverPlugin.useMacroParadise)
```

If you plan on writing tests, I recommend creating another (sub)project called
`tests` and add the `MacroRevolverPlugin.testCleanse` settings to it like so:

```scala
lazy val tests = project
  .in(file("tests"))
  .settings(MacroRevolverPlugin.useMacroParadise)
  .settings(MacroRevolverPlugin.testCleanse)
  .dependsOn(core)
```

This will cause your macro-dependent classes to be recompiled whenever you
change your macro code.

## The macro

Based on the above syntactic exploration, we know that we'll have to pimp a
little something onto `StringContext`. Let's go ahead and to that now:

```scala
object `package` {
  implicit class Args(ctx: StringContext) {
    object args {
      def unapply(unapplied: Array[String]): Any = macro ArgsMacros.unapplyImpl
    }
  }
}
```

- We use an `implicit class` over a `StringContext` to add an extractor onto
  `StringContext`.
- The extractor is called `args` and has an `unapply` that allows us to extract
  from values of type `Array[String]`, which matches the type used to pass CLI
  arguments to a `main` method.
- We implement the extractor using a macro called `ArgsMacros.unapplyImpl`.

Let's have a look at the macro:

```scala
class ArgsMacros(val c: whitebox.Context) {
  import c.universe._

  def unapplyImpl(unapplied: c.Expr[Array[String]]) = {
    val Select(Apply(_, List(Apply(_, parts))), _) = c.prefix.tree
    sys.error(s"""
      PARTS = $parts
    """)
  }
}
```

It's a pretty standard *extractor macro*:
- The input is of type `c.Expr[Array[String]]`, which matches the method input
  above, as well as the scrutinee of the match attempt.
- We have a scary-looking pattern match there, which extracts the textual
  `parts` of our `args"..."` syntax. This is passed as `c.prefix`, which is the
  contents of the `case` statement's pattern.

## Extracting the parts

How did we arrive at the extraction? Making sense of Scala's AST can be a pain,
so let's experiment a bit to see what we're getting. For this, let's come up
with some code that the macro will be applied to. Here we have a
super-simplified version of the `ssh` command with only a couple of arguments:

```scala
object ssh {
  def main(argv: Array[String]) {
    val args""" -l [$login_name]
      -v $verbose
    """ = argv
    println(s"login = ${login_name.value}")
    println(s"verbose = ${verbose.value}")
  }
}
```

In the body of our macro we'll simply go ahead and dump out the incoming tree:

```scala
def unapplyImpl(unapplied: c.Expr[Array[String]]) = {
}
```

It ends up looking like this:

```scala
literargs.`package`.Args(scala.StringContext.apply("\n      -l [", "]\n-v ", "\n    ")).args
```

Or, in AST form:

```scala
Select(
  Apply(
    Select(Select(Ident(literargs), literargs.package), TermName("Args")),
    List(
      Apply(
        Select(Select(Ident(scala), scala.StringContext), TermName("apply")),
        List(Literal(Constant("-l [")), Literal(Constant("] -v ")), Literal(Constant("")))
      )
    )
  ),
  TermName("args")
)
```

So this defines our pattern. The list of literal strings we're after, which is
basically the "language" by which we describe how CLI arguments should be
parsed, is plainly visible, and is what we'll ultimately extract. Most of what
the macro does will be based upon this list of strings.

In the next stage we'll discuss what can be done with this information.
