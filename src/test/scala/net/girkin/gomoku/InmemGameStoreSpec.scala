package net.girkin.gomoku

import java.util.UUID

import net.girkin.gomoku.game.{Game, InmemGameStore}
import org.scalatest.{Assertion, Matchers, WordSpec}
import zio.{Ref, Task, DefaultRuntime}

class InmemGameStoreSpec extends WordSpec with Matchers {
  val env = new DefaultRuntime {}

  def io(test: => Task[Assertion]): Unit = {
    env.unsafeRun(test)
  }

//  def withIO[A](m: Task[A])(test: A => Task[Assertion]): Unit = {
//    m.flatMap(test).unsafeRunSync()
//  }
//
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