package net.girkin.gomoku

import java.util.UUID

import net.girkin.gomoku.game.{Game, InmemGameStore}
import org.scalatest.{Assertion}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import zio.{Ref, Task}

class InmemGameStoreSpec extends AnyWordSpec with Matchers {
  val env = zio.Runtime.default

  def io(test: => Task[Assertion]): Unit = {
    env.unsafeRun(test)
  }

  def createStore: Task[InmemGameStore] = {
    Ref.make[List[Game]](List.empty).map {
      x => new InmemGameStore(x)
    }
  }

  "Memory game store" should {
    "pass simple test" in io {
      for {
        mem <- createStore
        gameAwaitingPlayers <- mem.getGamesAwaitingPlayers()
      } yield {
        gameAwaitingPlayers should be(List.empty)
      }
    }

    "be able to store games" in io {
      val newGame = Game.create(3, 3, 3)
      for {
        store <- createStore
        _ <- store.saveGameRecord(newGame)
        games <- store.getGamesAwaitingPlayers()
      } yield {
        games should have size 1
      }
    }

    "be able to update games" in io {
      val newGame = Game.create(3, 3, 3)
      val playerId = UUID.randomUUID()
      for {
        store <- createStore
        _ <- store.saveGameRecord(newGame)
        updatedGame = newGame.addPlayer(playerId)
        _ <- store.saveGameRecord(updatedGame)
        games <- store.getGamesAwaitingPlayers()
      } yield {
        games should have size 1
        games.head.players shouldBe List(playerId)
      }
    }
  }
}