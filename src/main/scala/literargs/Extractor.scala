package literargs

import cats.Functor

abstract class Extractor[F[_], T](protected implicit val F: Functor[F]) {
  def extract(raw: F[String]): F[T]
}

object Extractor {
  implicit def ExtractString[F[_]: Functor] = new Extractor[F, String] {
    def extract(raw: F[String]) = F.map(raw)(identity)
  }
  implicit def ExtractInt[F[_]: Functor] = new Extractor[F, Int] {
    def extract(raw: F[String]) = F.map(raw)(_.toInt)
  }
}
