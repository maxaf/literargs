# Stage -1

We're working in the runtime universe:

```tut
import scala.reflect.runtime.universe._
```

Scala AST quasiquotes allow us to use a simple compile-time Scala-like DSL to
describe and destructure Scala ASTs.

## Quasiquotes for creating ASTs

Let's explore quasiquotes, and figure out what they **actually are**:

```tut
val one = q"val foo = 1"
val two = q"""def greet(name: String) = println("hello, " + name + "!")"""
```

Quasiquotes allow us to **create Scala syntax trees** from plain strings at
compile time. This is pretty cool, considering that otherwise Scala's ASTs are
notoriously difficult to comprehend, let alone code by hand. Would you really
want to type all this manually? 😱

```tut
showRaw(one)
showRaw(two)
```

## Quasiquotes for matching ASTs

As shown above, ASTs look like `case class`-es; they're actually regular
classes for the most part, but many flavors of extractors are provided as part
of universe, thus easing access to parts of the structure:

```tut
// extract name
val ValDef(_, name, _, _) = q"val foo = 1"
```

```tut
// extract method name & body
val DefDef(_, name, _, _, _, body) = q"""def greet(name: String) = println("hello, " + name + "!")"""
```

There's an easier way to extract pieces of the syntax: by using **quasiquote
matchers**:

```tut
val q"val $name = $_"   =   ValDef(Modifiers(), TermName("foo"), TypeTree(), Literal(Constant(1)))
```

- On the right hand side we have a regular Scala AST.
- On the left hand side we have a quasiquote matcher that binds corresponding
  values from RHS to names:
  - `TermName("foo")` gets bound to `name`.
  - The literal `1` is thrown away because it's bound to `_`.

A quasiquote matcher likewise makes quick work of complex ASTs:

```tut
val q"def $name(..$paramss) = { ..$body }" = DefDef(
  Modifiers(),
  TermName("greet"),
  List(),
  List(List(ValDef(Modifiers(Flag.PARAM), TermName("name"), Ident(TypeName("String")), EmptyTree))),
  TypeTree(),
  Apply(
    Ident(TermName("println")),
    List(
      Apply(
        Select(
          Apply(
            Select(
              Literal(Constant("hello, ")),
              TermName("$plus")
            ),
            List(Ident(TermName("name")))
          ),
          TermName("$plus")
        ),
        List(Literal(Constant("!")))
      )
    )
  )
)
```

## Quasiquotes for other purposes

Scala ASTs are cool, but can quasiquotes be used for other purposes?

During this workshop, we'll explore a way to implement macros that make
possible the following syntax for parsing CLI arguments - let's use the
`ssh(1)` command as a guide:

```scala
def main(argv: Array[String]) {
  @usage def ssh {
    val args"""
      -1 $protocol_1 -2 $protocol_2 -4 $ipv4 -6 $ipv6
      -A $forward_agent -a $no_forward_agent -b [$bind_address]
      -C $compress -c [$cipher_spec] -D [$bind_address_port]
      -E [$log_file] -e [$escape_char] -F [$configfile] -f $background
      -G $dump_config -g $global_ports -I [$pkcs11] -i [$identity_file:Path]
      -K $enable_GSSAPI_auth -k $disable_GSSAPI_auth -L [$local_forward_address]
      -l [$login_name] -M $master -m [$mac_spec] -N $no_execute -n $no_stdin
      -O [$ctl_cmd] -o [..$option:OptionPair] -p [$port:Int] -Q [$query_option]
      -q $quiet -R [$remote_forward_address] -S [$ctl_path:Path] -s $subsystem
      -T $no_pty -t $force_pty -V $version -v $verbose -W [$host_port:Int]
      -w [$local_tun_remote_tun] -X $forward_x11 -x $disable_forward_x11
      -Y $trusted_forward_x11 -y $syslog
    """ = argv
  }
  println(s"login = ${login_name.value}")
  println(s"verbose = ${verbose.value}")
  println(s"option = ${option.value}")
}
```
