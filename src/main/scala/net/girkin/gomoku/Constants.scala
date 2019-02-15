package net.girkin.gomoku

import org.reactormonk.{CryptoBits, PrivateKey}

object Constants {
  val host = "localhost"
  val port = 9000

  val LoginUrl = "/auth/login"
  val authCookieName = "auth"

  val key = PrivateKey(scala.io.Codec.toUTF8(scala.util.Random.alphanumeric.take(20).mkString("")))
  val crypto = CryptoBits(key)
}
