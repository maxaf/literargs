# Stage 3

It's time to parse those CLI arguments! Libraries useful for this abound in the
Scala and JVM ecosystems, but they all come with baggage that makes them
cumbersome to use. For this exercise, I've decided to use
`scala-parser-combinators` to parse the CLI arguments.

## Parse the CLI args

First, let's come up with a data model as a target for our parser:

```scala
sealed trait Arg
case class BooleanArgs(matched: NonEmptyList[OptName]) extends Arg
case class ValueArg(matched: OptName, value: String) extends Arg
case class GarbageArg(garbage: String) extends Arg
```

We're reusing the `OptName` abstraction, which will link each parsed argument
to a specific `OptName`. The idea here is that a `List[Arg]` will provide
enough structure to exhaustively enumerate all arguments extracted from a
`Array[String]`.

Have a look at `src/main/scala/literargs/OptionParsers.scala` for the full
implementation of this parser, but the important parts are:

```scala
class OptionParsers(val opts: NonEmptyList[Opt]) extends CommonParsers {
  def parse(argv: Array[String]): Either[Either[Error,Failure], List[Arg]] = // ...
}
```

Here we turn a `NonEmptyList[Opt]` and a `Array[String]` into a `List[Arg]`.

## Connect parsed args to `Argument[M[_], T]`

Recall that in our haste to implement `ValueArgument[M[_]]` and
`BooleanArgument` we've written down `def value: Xor[Throwable, M[T]]` as a
`???` in both instances. Let's go ahead and use our parsed `List[Arg]` to
populate argument values.

For this, we're going to define the type class `Collect[M[_]]`, which looks
like this:

```scala
abstract class Collect[M[_]] {
  def collect(cmd: List[Arg], opt: Opt): Xor[Throwable, M[String]]
}
```

The type class defines a single method; based on its type signature it's easy
to determine that the method traverses a `List[Arg]` (which was parsed from an
`Array[String]`) and returns a `M[String]` pertaining to the given `Opt`.

Expressing this functionality via a type class allows us to provide different
implementations for different `M[_]`-s. In
`src/main/scala/literargs/Collect.scala` we go on to do exactly that by
implementing a `Collect[Id]` for single required values,
`Collect[NonEmptyList]` for multiple required values, and a
`Collect[M[_]:MonadFilter]` for all other container types, such as `Set`,
`List`, etc.

## Using `Collect[M[_]]`

Let's update `ValueArgument[M[_]]` now:

```scala
class ValueArgument[M[_]](val opt: Opt)(implicit cmd: List[Arg], C: Collect[M]) extends Argument[M, String] {
  def value = C.collect(cmd, opt)
}
```

Here we provide an implicit `List[Arg]` and `Collect[M]`, and use these to
return the value relating to the given `Opt`.

But what about `BooleanArgument`? Since we only capture the mere presence of
boolean switches via a `NonEmptyList[OptName]`, we can't use a `Collect`
instance here, as it would only be able to operate on switches with values. So,
we will simply traverse the `List[Arg]` in search of a matching argument:

```scala
class BooleanArgument(val opt: Opt)(implicit cmd: List[Arg]) extends Argument[Id, Boolean] {
  def value = Xor.right(cmd.collect {
    case BooleanArgs(matched) if matched.exists(_ == opt.name) => opt.name
  }.nonEmpty)
}
```

## Supporting other data types with `Extractor[F[_], T]`

So far we've been targeting options with strictly `String` arguments, as well
as boolean switches. We can add support for more types by introducing another
type class: `Extractor[F[_], T]`.

This type class allows us to convert `String` **=>** some `T` inside of a
functor `F[_]`.

For example, to allow us to specify command-line arguments of the worm `-p
<$path:java.nio.file.Path>`, we can provide a `Extractor` instance as follows:

```scala
import java.nio.file.Path
implicit def ExtractPath[F[_]: Functor] = new Extractor[F, Path] {
  def extract(raw: F[String]) = F.map(raw)(Paths.get(_))
}
```

This paves the way to fully type-safe command line arguments.
