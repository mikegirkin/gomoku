package net.girkin.gomoku

import java.sql.Connection

trait Database {
  def withConnection[T](action: Connection => T): T
}
