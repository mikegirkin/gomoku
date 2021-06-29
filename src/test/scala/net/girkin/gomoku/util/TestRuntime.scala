package net.girkin.gomoku.util

trait TestRuntime {
  implicit val ec = scala.concurrent.ExecutionContext.global
  implicit val rt = zio.Runtime.default
}
