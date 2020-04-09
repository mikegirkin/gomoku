package net.girkin.gomoku

import java.sql.Connection
import com.mchange.v2.c3p0.ComboPooledDataSource

trait Database {
  def withConnection[T](action: Connection => T): T
}

class PsqlPooledDatabase extends Database {

  val dataSource = mkDataSource()

  private def mkDataSource(): ComboPooledDataSource = {
    val cpds = new ComboPooledDataSource
    //TODO: Read from config
    cpds.setDriverClass("org.postgresql.Driver")
    cpds.setJdbcUrl("jdbc:postgresql://localhost/gomoku")

    cpds
  }

  override def withConnection[T](action: Connection => T): T = {
    val connection = dataSource.getConnection
    connection.setAutoCommit(false)
    try {
      val result = action(connection)
      connection.commit()
      connection.close()
      result
    } catch {
      case ex: Exception =>
        connection.rollback()
        connection.close()
        throw ex
    }

  }
}