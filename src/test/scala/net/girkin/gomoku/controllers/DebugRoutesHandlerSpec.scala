package net.girkin.gomoku.controllers

import net.girkin.gomoku.AuthUser
import net.girkin.gomoku.api.{DebugRoutesHandler, OutboundChannels}
import net.girkin.gomoku.game.{Active, GameStore, GameStoreRecord, PlayerNumber}
import net.girkin.gomoku.util.TestRuntime
import org.scalamock.scalatest.MockFactory
import org.scalatest.Inside
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import zio.ZIO

import java.time.Instant
import java.util.UUID

class DebugRoutesHandlerSpec extends AnyWordSpec
  with Matchers with Inside with MockFactory with TestRuntime {

  val environmentF = for {
    _channels <- OutboundChannels.make()
    _gameStore = mock[GameStore]
  } yield {
    val _handler = new DebugRoutesHandler(_channels, _gameStore)
    new {
      val channels: OutboundChannels = _channels
      val handler: DebugRoutesHandler = _handler
      val gameStore: GameStore = _gameStore
    }
  }

  "GameRoutesHandler 'listChannels'" should {
    "return list of known channels" in {
      val testF = for {
        env <- environmentF
        authUser1 = AuthUser(UUID.randomUUID())
        authUser2 = AuthUser(UUID.randomUUID())
        _ <- env.channels.findOrCreateUserChannel(authUser1)
        _ <- env.channels.findOrCreateUserChannel(authUser2)
        channelList <- env.handler.listChannels(authUser1)
      } yield {
        channelList should contain theSameElementsAs List(
          s"User: ${authUser1.userId}",
          s"User: ${authUser2.userId}"
        )
      }

      rt.unsafeRun(testF)
    }
  }

  "GameRoutesHandler 'listGames'" should {
    "return list of known games" in {
      val gameList = List(
        GameStoreRecord(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), 3, 3, 3, Active(PlayerNumber.First)),
        GameStoreRecord(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), 5, 5, 5, Active(PlayerNumber.Second))
      )
      val testF = for {
        env <- environmentF
        _ = (env.gameStore.listGames _).expects().returns(
          ZIO.succeed(gameList)
        )
        gameList <- env.handler.listGames()
      } yield {
        gameList should contain theSameElementsAs(gameList)
      }

      rt.unsafeRun(testF)
    }
  }
}
