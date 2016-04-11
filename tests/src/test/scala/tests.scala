package tests

import literargs._
import cats.Functor
import cats.implicits._
import java.nio.file.{ Path, Paths }

object `package` {
  implicit def ExtractPath[F[_]: Functor] = new Extractor[F, Path] {
    def extract(raw: F[String]) = F.map(raw)(Paths.get(_))
  }
  implicit def ExtractOptionPair[F[_]: Functor] = new Extractor[F, OptionPair] {
    def extract(raw: F[String]) = F.map(raw)(_.split('=') match {
      case Array(key, value) => OptionPair(key, value)
      case x => sys.error(s"WTF: ${x.toList}")
    })
  }
}

case class OptionPair(key: String, value: String)

object ssh {
  def main(argv: Array[String]) {
    @usage def ssh {
      val args"""
        -1 $protocol_1 -2 $protocol_2
        -4 $ipv4 -6 $ipv6
        -A $forward_agent -a $no_forward_agent
        -b [$bind_address]
        -C $compress
        -c [$cipher_spec]
        -D [$bind_address_port]
        -E [$log_file]
        -e [$escape_char]
        -F [$configfile]
        -f $background
        -G $dump_config
        -g $global_ports
        -I [$pkcs11]
        -i [$identity_file:Path]
        -K $enable_GSSAPI_auth -k $disable_GSSAPI_auth
        -L [$local_forward_address]
        -l [$login_name]
        -M $master
        -m [$mac_spec]
        -N $no_execute -n $no_stdin
        -O [$ctl_cmd]
        -o [..$option:OptionPair]
        -p [$port:Int]
        -Q [$query_option]
        -q $quiet
        -R [$remote_forward_address]
        -S [$ctl_path:Path]
        -s $subsystem
        -T $no_pty -t $force_pty
        -V $version
        -v $verbose
        -W [$host_port:Int]
        -w [$local_tun_remote_tun]
        -X $forward_x11 -x $disable_forward_x11 -Y $trusted_forward_x11
        -y $syslog
      """ = argv
    }
    println(s"login = ${login_name.value}")
    println(s"verbose = ${verbose.value}")
    println(s"option = ${option.value}")
  }
}
