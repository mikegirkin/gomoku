package net.girkin.gomoku

import cats.effect.Sync
import org.slf4j.LoggerFactory
import zio.UIO

trait Logging {
  val logger = LoggerFactory.getLogger(this.getClass.toString)
}

trait FunctionalLogging {
  private[this] val logger = LoggerFactory.getLogger(this.getClass.toString)

  private def logIO(fn: String => Unit)(data: String): UIO[Unit] = {
    UIO.succeed {
      fn(data)
    }
  }

  def trace(msg: String): UIO[Unit] = {
    logIO(logger.trace)(msg)
  }

  def debug(msg: String): UIO[Unit] = {
    logIO(logger.debug)(msg)
  }

  def info(msg: String): UIO[Unit] = {
    logIO(logger.info)(msg)
  }

  def warn(msg: String): UIO[Unit] = {
    logIO(logger.warn)(msg)
  }

  def error(msg: String): UIO[Unit] = {
    logIO(logger.error)(msg)
  }

}
