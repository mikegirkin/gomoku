package net.girkin.gomoku

import java.util.UUID

import cats._
import cats.implicits._
import net.girkin.gomoku.game._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{Inside, Inspectors}
import org.scalatest.matchers.should.Matchers


class GameSpec extends AnyWordSpec
  with Matchers with Inside with Inspectors {

  val firstUser = UUID.randomUUID()
  val secondUser = UUID.randomUUID()

  "Game" should {
    val game = Game.create(GameRules(7, 5, 5), firstUser, secondUser)

    val move1 = MoveAttempt(2, 2, firstUser)
    val result = game.makeMove(move1)
    val gameAfterMove1 = result.toOption.get

    "be able to handle correct moves" in {
      gameAfterMove1.status shouldBe Active(PlayerNumber.Second)
      gameAfterMove1.field.get(2, 2) shouldBe Some(PlayerNumber.First)
    }

    "be able to handle incorrect moves" in {
      val testSet = List[(Game, MoveAttempt, MoveError)](
        (game, MoveAttempt(-1, 2, secondUser), ImpossibleMove("smth")),
        (game, MoveAttempt(2, 2, secondUser), ImpossibleMove("smth")),
        (gameAfterMove1, MoveAttempt(2, 3, firstUser), ImpossibleMove("smth"))
      )

      forAll(testSet) {
        case (gameUnderTest, move, result) =>
          inside(gameUnderTest.makeMove(move)) {
            case Left(err) => err.getClass shouldBe result.getClass
          }
      }
    }

    val userQuitGame = gameAfterMove1.playerConceded(PlayerNumber.Second)

    "sets the correct state when one of the players leave before finish" in {
      userQuitGame.status shouldBe Finished(PlayerQuit(PlayerNumber.Second))
    }

    "prevents removing player from inactive game" in {
      userQuitGame.playerConceded(PlayerNumber.Second) shouldBe userQuitGame
    }
  }

  "Game winner logic" should {
    val gameWithUsers = Game.create(GameRules(5, 5, 3), firstUser, secondUser)

    val moveTests =
      List(
        List(
          MoveAttempt(0, 0, firstUser),
          MoveAttempt(0, 1, secondUser),
          MoveAttempt(1, 1, firstUser),
          MoveAttempt(1, 2, secondUser),
          MoveAttempt(2, 2, firstUser)
        ) -> PlayerNumber.First,
        List(
          MoveAttempt(0, 0, firstUser),
          MoveAttempt(1, 1, secondUser),
          MoveAttempt(0, 2, firstUser),
          MoveAttempt(2, 2, secondUser),
          MoveAttempt(0, 4, firstUser),
          MoveAttempt(3, 3, secondUser)
        ) -> PlayerNumber.Second
      )



    "be able to figure out the end of the game " in {

      forAll(moveTests) { moves =>
        val finishedGame = Foldable[List].foldM(moves._1, gameWithUsers)( (game, move) => {
          game.status shouldBe a[Active]
          game.makeMove(move)
        })

        inside(finishedGame) {
          case Right(value) =>
            value.status shouldBe Finished(PlayerWon(moves._2))
        }
      }

    }

    "be able to figure out draw situation" in {
      val gameWithUsers = Game.create(GameRules(3, 3, 3), firstUser, secondUser)

      /*
      XOX
      XOX
      OXO
     */
      val moves = List(
        (0, 0),
        (1, 1),
        (2, 1),
        (2, 0),
        (0, 2),
        (0, 1),
        (1, 2),
        (2, 2),
        (1, 0)
      ).zipWithIndex.map {
        case ((row, column), index) =>
          MoveAttempt(row, column, if(index % 2 == 0) firstUser else secondUser)
      }

      val finishedGame = Foldable[List].foldM(moves, gameWithUsers)( (game, move) => {
        game.status shouldBe a[Active]
        game.makeMove(move)
      })

      inside(finishedGame) {
        case Right(value) =>
          value.status shouldBe Finished(Draw)
      }
    }
  }
}
