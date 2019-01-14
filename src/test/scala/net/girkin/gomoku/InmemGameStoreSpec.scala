package net.girkin.gomoku

import java.util.UUID

import cats.effect.IO
import fs2.async.Ref
import net.girkin.gomoku.game.{Game, InmemGameStore}
import org.scalatest.{Assertion, Matchers, WordSpec}

class InmemGameStoreSpec extends WordSpec with Matchers {
  def io(test: => IO[Assertion]): Unit = {
    test.unsafeRunSync()
  }

  def withIO[A](m: IO[A])(test: A => IO[Assertion]): Unit = {
    m.flatMap(test).unsafeRunSync()
  }

  def createStore: IO[InmemGameStore[IO]] = {
    Ref[IO, List[Game]](List.empty).map {
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