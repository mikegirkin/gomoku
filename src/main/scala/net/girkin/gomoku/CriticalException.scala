package net.girkin.gomoku

import net.girkin.gomoku.game.GameStore

object CriticalException {
  final case class DatabaseError(wrapped: GameStore.StoreError) extends Exception
}
