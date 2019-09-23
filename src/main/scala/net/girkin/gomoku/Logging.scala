package net.girkin.gomoku

import cats.effect.Sync
import org.slf4j.LoggerFactory

trait Logging {
  val logger = LoggerFactory.getLogger(this.getClass.toString)
}

trait FunctionalLogging[F[_]] {
  private val logger = LoggerFactory.getLogger(this.getClass.toString)

  private def logIO(fn: String => Unit)(data: String)(implicit ev: Sync[F]): F[Unit] = {
    Sync[F].delay {
      fn(data)
    }
  }

  def trace(msg: String)(implicit ev: Sync[F]): F[Unit] = {
    logIO(logger.trace)(msg)
  }

  def debug(msg: String)(implicit ev: Sync[F]): F[Unit] = {
    logIO(logger.debug)(msg)
  }

  def info(msg: String)(implicit ev: Sync[F]): F[Unit] = {
    logIO(logger.info)(msg)
  }

  def warn(msg: String)(implicit ev: Sync[F]): F[Unit] = {
    logIO(logger.warn)(msg)
  }

  def error(msg: String)(implicit ev: Sync[F]): F[Unit] = {
    logIO(logger.error)(msg)
  }

}
