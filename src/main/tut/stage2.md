# Stage 2

```tut:silent
// we're going to need the runtime universe again
import scala.reflect.runtime.universe._
```

Now that we have a data model and a parser for the CLI arguments specification
DSL we've invented on the spot, we need to figure out how to put these things
to work for us.

## The `unapply`

Remember now that our intent is to build a macro-generated `unapply` for CLI
options. The `unapply` is generated according to general guidance [in
macro-paradise
documentation](http://docs.scala-lang.org/overviews/macros/extractors.html).

To make this happen, we'll adjust our `unapplyImpl` macro implementation as
follows:

```scala
def unapplyImpl(unapplied: c.Expr[Array[String]]) = {
  val Select(Apply(_, List(Apply(_, parts))), _) = c.prefix.tree

  // parse the options into a data structure
  val opts: List[Opt] =
    (new DslParser).parse_!(extractParts(parts)) match {
      case Right(parsed) => parsed
      case Left(err) => c.abort(c.prefix.tree.pos, s"problem: ${err.getClass}: $err")
    }

  // generate the AST
  q"""
    new {
      class Match(argv: Array[String]) {
        def get = this
        def isEmpty = false
      }
      def unapply(argv: Array[String]) = new Match(argv)
    }.unapply($unapplied)
  """
}
```

The idea here is that `class Match` will be instantiated every time an
`Array[String]` of CLI arguments is available for parsing. **Exactly how** the
arguments are parsed will depend on the `opts: List[Opt]` data structure we've
previously parsed from our DSL. We will generate the `unapply`'s behavior to
reflect our `List[Opt]`.

## Arity

Let's go ahead and try to compile our sample `ssh(1)`-like CLI args definition.
Uh-oh!

```
[error] tests.scala:10: too many patterns for <$anon: AnyRef> offering Match: expected 1, found 5
[error]     val args"""
[error]         ^
```

This tells us that we've returned one result where 5 were expected. Why 5, you
ask? That's because we've defined 5 argument bindings, but our `class Match`
doesn't implement the `_1`, `_2`, ..., `_N` fields expected of it for returning
multiple matches.

Let's go ahead and adjust the implementation:

```scala
val getters = opts.zipWithIndex.map {
  case (opt, idx) =>
    q"def ${TermName(s"_${idx + 1}")} = ???"
}

q"""
  new {
    class Match(argv: Array[String]) {
      def get = this
      ..$getters
      def isEmpty = false
    }
    def unapply(argv: Array[String]) = new Match(argv)
  }.unapply($unapplied)
"""
```

Splicing the placeholder numbered getters does the trick, so now we can move
on.

## The result type

Let's stop to think for a moment about how we want to expose the results of
parsing CLI arguments to the developers who will use our library. It's
generally a good idea to make the potential for failure explicit;
`cats.data.Xor` is a good type for this, so we'll go with that. We can make the
LHS of `Xor` take a `Throwable`, while the RHS will be a `String` representing
the actual argument value.

But wait - didn't we say that we've intended to support multi-argument options?
This means that `String` as a type is unsufficient for our purposes. What we
really need is `Xor[Throwable, M[String]]`, which will allow us to express
single-argument options as `Id[String]`, optional multi-argument options as
`List[String]`, and required multi-argument options as `NonEmptyList[String]`.
The power of types compels you!

Thus we'll go ahead and adjust our generated numbered getters to use our new
result type:

```scala
val getters = opts.zipWithIndex.map {
  case (opt, idx) =>
    q"def ${TermName(s"_${idx + 1}")}: cats.data.Xor[Throwable, M[String]] = ???"
}
```

...Except we don't have a handle on this `M[_]` type here. We need to make more
structure here.

## Structuring the numbered getters

Let's create a type for each numbered getter that will better represent the
nature of what each getter is trying to achieve; we'll call this type
`Argument[M[_], T]`, where `T` is the actual value type, which could be
`String` or `Boolean`, since we have boolean options as well:

```scala
sealed trait Argument[M[_]] {
  def opt: Opt
  def value: Xor[Throwable, M[String]]
}
```

We can also create a couple of concrete instances while we're at it, one for
values and one for boolean flags:

```scala
class ValueArgument[M[_]](val opt: Opt) extends Argument[M, String] {
  def value = ???
}

class BooleanArgument(val opt: Opt) extends Argument[Id, Boolean] {
  def value = ???
}
```

Thus the getter generation code will now look like this:

```scala
val getters = opts.zipWithIndex.map {
  case (opt, idx) =>
    val monad = opt.hole match {
      case ValueHole(Unary(true)) | BooleanHole => tq"cats.Id"
      case ValueHole(Unary(false))              => tq"Option"
      case ValueHole(N_ary(true))               => tq"cats.data.NonEmptyList"
      case ValueHole(N_ary(false))              => tq"List"
    }
    val name = TermName(s"_${idx + 1}")
    opt.hole match {
      case ValueHole(_) =>
        q"def $name: Argument[$monad, String] = new ValueArgument[$monad](opt = ???)"
      case BooleanHole =>
        q"def $name: Argument[$monad, Boolean] = new BooleanArgument(opt = ???)"
    }
}
```

## Reify the `opt`

You might have noticed the `opt = ???` part: `opt` is a reference to our option
descriptor of type `Opt`, which we hold in memory while our macro executes. To
be able to splice this reference into the AST we're generating with `q""`
quasiquotes, we must convert the reference to AST nodes. This can be done
inline, or by providing a `Liftable` type class instance for each type that we
wish to convert to AST nodes.
```scala
import scala.reflect.macros.whitebox
abstract class Liftables[C <: whitebox.Context](val c: C) {
  import c.universe._
  implicit object liftArity extends Liftable[Arity] {
    def apply(arity: Arity) = arity match {
      case Unary(required) => q"Unary($required)"
      case N_ary(required) => q"N_ary($required)"
    }
  }
  implicit object liftHole extends Liftable[Hole] {
    def apply(hole: Hole) = hole match {
      case ValueHole(arity, ascription) => q"ValueHole($arity, $ascription)"
      case BooleanHole => q"BooleanHole"
    }
  }
  implicit object liftOptName extends Liftable[OptName] {
    def apply(name: OptName) = q"OptName(${name.short}, ${name.long})"
  }
  implicit object liftOpt extends Liftable[Opt] {
    def apply(opt: Opt) = q"Opt(${opt.name}, ${opt.hole})"
  }
}
```

After this, we can change the numbered getter generation code as follows:
```scala
case ValueHole(_) =>
  q"def $name: Argument[$monad, String] = new ValueArgument[$monad](opt = $opt)"
case BooleanHole =>
  q"def $name: Argument[$monad, Boolean] = new BooleanArgument(opt = $opt)"
```

In the next stage we'll look into parsing the actual CLI arguments & putting
the `Argument` type to good use.
