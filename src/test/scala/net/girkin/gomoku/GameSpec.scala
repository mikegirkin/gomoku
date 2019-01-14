package net.girkin.gomoku

import java.util.UUID

import cats._
import cats.implicits._
import net.girkin.gomoku.game._
import org.scalatest.{Inside, Inspectors, Matchers, WordSpec}


class GameSpec extends WordSpec
  with Matchers with Inside with Inspectors {

  val firstUser = UUID.randomUUID()
  val secondUser = UUID.randomUUID()

  "Game" should {
    val game = Game.create(7, 5, 5)

    "be initialized properly after creation" in {
      game.players shouldBe empty
      game.field.height shouldBe 7
      game.field.width shouldBe 5
      game.status shouldBe WaitingForUsers
    }

    val gameWithOneUser = game.addPlayer(firstUser)

    "be able to handle addition of the first user" in {
      gameWithOneUser.status shouldBe WaitingForUsers
      gameWithOneUser.players shouldBe List(firstUser)
    }

    val gameWithUsers = gameWithOneUser.addPlayer(secondUser)

    "be able to handle addition of the second user" in {
      gameWithUsers.status shouldBe Active(PlayerNumber.First)
      gameWithUsers.players shouldBe List(firstUser, secondUser)
    }

    val move1 = MoveAttempt(2, 2, firstUser)
    val gameAfterMove1 = gameWithUsers.makeMove(move1).right.get

    "be able to handle correct moves" in {
      gameAfterMove1.status shouldBe Active(PlayerNumber.Second)
      gameAfterMove1.field.get(2, 2) shouldBe Some(PlayerNumber.First)
    }

    "be able to handle incorrect moves" in {
      val testSet = List[(Game, MoveAttempt, MoveError)](
        (gameWithOneUser, MoveAttempt(2, 5, firstUser), GameNotStarted),
        (gameWithUsers, MoveAttempt(-1, 2, secondUser), ImpossibleMove("smth")),
        (gameWithUsers, MoveAttempt(2, 2, secondUser), ImpossibleMove("smth")),
        (gameAfterMove1, MoveAttempt(2, 3, firstUser), ImpossibleMove("smth"))
      )

      forAll(testSet) {
        case (gameUnderTest, move, result) =>
          inside(gameUnderTest.makeMove(move)) {
            case Left(err) => err.getClass shouldBe result.getClass
          }
      }
    }

    val userQuitGame = gameAfterMove1.removePlayer(secondUser)

    "sets the correct state when one of the players leave before finish" in {
      userQuitGame.status shouldBe Finished(PlayerQuit(PlayerNumber.Second))
    }

    "prevents removing player from inactive game" in {
      userQuitGame.removePlayer(secondUser) shouldBe userQuitGame
    }
  }

  "Game winner logic" should {
    val gameWithUsers = Game.create(5, 5, 3)
      .addPlayer(firstUser)
      .addPlayer(secondUser)

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

        finishedGame.right.get.status shouldBe Finished(PlayerWon(moves._2))
      }

    }

    "be able to figure out draw situation" in {
      val gameWithUsers = Game.create(3, 3, 3)
        .addPlayer(firstUser)
        .addPlayer(secondUser)

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

      finishedGame.right.get.status shouldBe Finished(Draw)
    }
  }
}
