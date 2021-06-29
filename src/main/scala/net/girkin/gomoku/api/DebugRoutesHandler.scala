package net.girkin.gomoku.api

import net.girkin.gomoku.AuthUser
import net.girkin.gomoku.game.GameStore.StoreError
import net.girkin.gomoku.game.{GameStore, GameStoreRecord}
import org.http4s.dsl.Http4sDsl
import zio.{IO, Task}

class DebugRoutesHandler(
  userChannels: OutboundChannels,
  gameStore: GameStore
) extends Http4sDsl[Task] {
  def listGames(): IO[StoreError, List[GameStoreRecord]] = {
    gameStore.listGames()
  }

  def listChannels(token: AuthUser): Task[List[String]] = {
    for {
      channels <- userChannels.list()
      channelsData = channels.map {
        case (user, queue) => s"User: ${user.userId}"
      }.toList
    } yield {
      channelsData
    }
  }
}
