package net.girkin.gomoku

import cats.implicits._

object Ops {

  implicit class Pipe[A](val a: A) extends AnyVal {
    def |>[B](f: A => B): B = f(a)
  }

  implicit class OptionPipe[A](val a: Option[A]) extends AnyVal {
    def ?>[B](leftSide: => B): Either[B, A] = {
      Either.fromOption(a, leftSide)
    }
  }

}
