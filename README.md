# literargs: literate command-line arguments parser

Configuration, logging, and CLI arguments parser libraries are all far from
endangered species. Why create another one?

It's fair to say that parsing command line arguments in Scala is a solved
problem. There are quite [a few libraries](https://github.com/scopt/scopt)
that [do exactly that](https://github.com/scallop/scallop), and do it [pretty
well](http://software.clapper.org/argot/). There's also an army of Java
libraries that do the same thing with varying degrees of style.

The problem with all of the above is this: IMHO it is counterintuitive to
learn & use an API that has little to do with the way CLI applications are
used, and with how such applications display "usage" text. Each CLI arguments
parsing library has its own API that seems to address a goal completely
orthogonal to the task of parsing CLI arguments & generating a human-readable
help/usage text. This, in my mind, qualifies as a distraction.

What I wanted is a fluent description language that specifies the interface of
a CLI application. That description language (call it a DSL if you must)
should be used both to parse CLI arguments *and* display help/usage text to
the user.  This library - `literargs` - achieves exactly that.

## A brief example

```scala
import literargs._
import cats.implicits._

object hello {
  def main(argv: Array[String]): Unit = {
    val args"""
      -l $living
      -n <$name>
      -a [$age:Int]
      -i <..$interests>
      -c [..$accomplishments]
    """ = argv
    println(s"""
      hello, ${name.value}!
      you're aged ${age.value}
      you like ${interests.value.map(_.toList.mkString(", "))}
      and you've accomplished ${accomplishments.value.map(_.mkString(", "))}
    """)
    if (!living.value.valueOr(_ => false))
      println("alas, you're no longer with us. :(")
  }
}
```

Let's try to run this now:

```scala
hello.main(Array(
  "-n", "Albert Einstein",
  "-a", "137",
  "-i", "physics",
  "-i", "cosmology",
  "-i", "wonders of the universe",
  "-c", "Nobel Prize",
  "-c", "New Jersey Hall Of Fame"
))
// 
//       hello, Right(Albert Einstein)!
//       you're aged Right(Some(137))
//       you like Right(physics, cosmology, wonders of the universe)
//       and you've accomplished Right(Nobel Prize, New Jersey Hall Of Fame)
//     
// alas, you're no longer with us. :(
```

### Quasiquotes for CLI arguments?

So, we have a `argv: Array[String]` that we want to parse into meaningful
values. Here `val args""" ... """ = argv` is an extractor that turns the
provided arguments array into a bunch of values, in this case, `name`, `age`,
and `interests`.

You can think of the `val args""" ... """ = argv` piece as a DSL block that
configures and invokes an extractor. The quoted text - `"""-n <$name> -a
[$age:Int] -i [..$interests]"""` - is used similarly to quasiquote extractors.
The difference that instead of Scala-like syntax used to extract pieces of the
Scala AST, here we use a syntax similar to "usage text" that shows up when a
CLI application is run with the `--help` option. After each option description
(i.e. `-i`) comes a type description with a binding inside of it:
`[..$interests]`.

### But think of the children`^W` types!

Let's examine in more detail the types of values bound by the `args` extractor.
Stringly typing an entire application based on contents of `Array[String]`
isn't anyone's idea of fun, after all.

```scala
scala> val args"-l $living -n <$name> -a [$age:Int] -i <..$interests> -c [..$accomplishments]" =
     | Array(
     |   "-n", "Albert Einstein",
     |   "-a", "137",
     |   "-i", "physics",
     |   "-i", "cosmology",
     |   "-i", "wonders of the universe",
     |   "-c", "Nobel Prize",
     |   "-c", "New Jersey Hall Of Fame"
     | )
living: literargs.Argument[cats.Id,Boolean] = $anon$1$Match$`l`$macro$6$@70945a32
name: literargs.Argument[cats.Id,String] = $anon$1$Match$`n`$macro$7$@26d6a7d0
age: literargs.Argument[Option,Int] = $anon$1$Match$`a`$macro$8$@1ae02577
interests: literargs.Argument[cats.data.NonEmptyList,String] = $anon$1$Match$`i`$macro$9$@2ac848d7
accomplishments: literargs.Argument[List,String] = $anon$1$Match$`c`$macro$10$@78dfd276
```

We get three symbols - `name`, `age`, and `interests` - of type `Argument[M[_],
A]`. The `Argument` type is defined by `literargs` as follows:

```scala
sealed trait Argument[M[_], T] {
  def value: Either[Throwable, M[T]]
}
```

The purpose of `Argument` is clear: it encapsulates an `M[T]`, where `M[_]` is
some type constructor that contains zero, one, or multiple values of type `T`.
Syntax within the `val args""" ... """` extractor controls which `M[_]` and `T`
types are chosen by the macro.

We see three `M[_]` types here: `Id`, `Option` and `List`. With the
exception of `age` (which is an `Int`), the other two `T` types are `String`.
How does this relate to the syntax we used to describe our options?

| Definition syntax         | Bound to symbol   | `M[_]` type         | Value type |
| -----------------         | ---------------   | -----------         | ---------- |
| `-l $living`              | `living`          | `Id`                | `Boolean`  |
| `-n <$name>`              | `name`            | `Id`                | `String`   |
| `-a [$age:Int]`           | `age`             | `Option`            | `Int`      |
| `-i <..$interests>`       | `interests`       | `NonEmptyList`      | `String`   |
| `-c [..$accomplishments]` | `accomplishments` | `List`              | `String`   |

In plain English, this means that:

* Bindings without any adornments are Boolean flags.
* Bindings surrounded by `<` and `>` are considered required.
* Bindings surrounded by `[` and `]` are considered optional.
* Bindings with `..` before the name will accept multiple values.
  * Required `..` bindings turn into a `NonEmptyList`.
  * Optional `..` bindings turn into a `List`.

Let's check out some of the values now:

```scala
scala> living.value
res2: Either[Throwable,cats.Id[Boolean]] = Right(false)

scala> name.value
res3: Either[Throwable,cats.Id[String]] = Right(Albert Einstein)

scala> age.value
res4: Either[Throwable,Option[Int]] = Right(Some(137))

scala> interests.value
res5: Either[Throwable,cats.data.NonEmptyList[String]] = Right(NonEmptyList(physics, cosmology, wonders of the universe))

scala> accomplishments.value
res6: Either[Throwable,List[String]] = Right(List(Nobel Prize, New Jersey Hall Of Fame))
```

We see here that types of values are assigned depending on what syntax was used
to adorn bindings.

### Custom types

The `-c [..$accomplishments]` option above is a `List[String]` detailing the
accomplishments of one famous scientist. What if we wanted to keep a
`List[Accomplishment]`, containing both the accomplishment *and* the year when
it was achieved? Let's adorn the option definition with our desired type:

```scala
import literargs._
import cats.implicits._
```

```scala
scala> {
     |   case class Accomplishment(year: Int, description: String)
     |   val args"""
     |     -l $living
     |     -n <$name>
     |     -a [$age:Int]
     |     -i <..$interests>
     |     -c [..$accomplishments:Accomplishment]
     |   """ =
     |     Array(
     |       "-n", "Albert Einstein",
     |       "-a", "137",
     |       "-i", "physics",
     |       "-i", "cosmology",
     |       "-i", "wonders of the universe",
     |       "-c", "Nobel Prize",
     |       "-c", "New Jersey Hall Of Fame"
     |     )
     | }
<console>:21: error: could not find implicit value for parameter E: literargs.Extractor[List,Accomplishment]
  val args"""
      ^
<console>:22: error: recursive value x$1 needs type
    -l $living
        ^
```

This fails to compile because `literargs` doesn't know how to turn `String`-s
inside `Array[String]` into instances of `Accomplishment`. This is easy to fix
by providing the requisite type class instance:

```scala
import literargs._
import cats.Functor
import cats.implicits._
```

```scala
case class Accomplishment(year: Int, description: String)
// defined class Accomplishment

implicit def ExtractAccomplishment[F[_]: Functor] =
  new Extractor[F, Accomplishment] {
    def extract(raw: F[String]) =
      F.map(raw)(_.split(':') match {
        case Array(year, description) => Accomplishment(year.toInt, description)
      })
  }
// ExtractAccomplishment: [F[_]](implicit evidence$1: cats.Functor[F])literargs.Extractor[F,Accomplishment]

val args"""
  -l $living
  -n <$name>
  -a [$age:Int]
  -i <..$interests>
  -c [..$accomplishments:Accomplishment]
""" =
  Array(
    "-n", "Albert Einstein",
    "-a", "137",
    "-i", "physics",
    "-i", "cosmology",
    "-i", "wonders of the universe",
    "-c", "Nobel Prize",
    "-c", "New Jersey Hall Of Fame"
  )
// living: literargs.Argument[cats.Id,Boolean] = $anon$1$Match$`l`$macro$16$@4f99917e
// name: literargs.Argument[cats.Id,String] = $anon$1$Match$`n`$macro$17$@1bc20542
// age: literargs.Argument[Option,Int] = $anon$1$Match$`a`$macro$18$@61ee516b
// interests: literargs.Argument[cats.data.NonEmptyList,String] = $anon$1$Match$`i`$macro$19$@5a6566c1
// accomplishments: literargs.Argument[List,Accomplishment] = $anon$1$Match$`c`$macro$20$@59f40e92
```

Here we have provided an instance of `Extractor[F[_], Accomplishment]` that
turns `String`-s into `Accomplishment`-s. A couple of things to note:

* We must abstract over `F[_]` because in the field it might be a `Id`,
  `Option`, `List`, `NonEmptyList`, or some other thing.
* This `Extractor` performs a couple of dangerous operations, which, in case of
  failure, would make use of the LHS of `def value: Either[Throwable, M[T]]` on
  `Argument[M[_], T]`.

## What now?

Currently `literargs` is in its larval stage: the basic building blocks are
there, but there's still a lot of work to be done in a number of areas,
including but not limited to:

* Error handling and messaging to the programmer at compile time.
* Error handling and messaging to the CLI user at run time.
* Support for more CLI option description syntax; perhaps [docopt](http://docopt.org/)?
* Macros in this project care little for potential symbol collisions.
* Generation of help/usage text to the user.
* Parsing of `argv: Array[String]` is accomplished by a custom parser that is
  probably broken in numerous ways. This was easier than using another library
  as the parsing backend (as shown by the extinct `commons-cli` integration
  that has gone sour), but is perhaps not the best approach long term.

In addition, option description syntax is subject to change, and may not be
flexible enough for all use cases. YMMV!
