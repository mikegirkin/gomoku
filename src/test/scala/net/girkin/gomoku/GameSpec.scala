package net.girkin.gomoku

import java.util.UUID

import cats.data.State
import net.girkin.gomoku
import net.girkin.gomoku.PlayerNumber.{First, Second}
import org.scalatest.{Inside, Inspectors, Matchers, WordSpec}

class GameSpec extends WordSpec
  with Matchers with Inside with Inspectors {

  "Game" should {
    val game = Game.create(7, 5, 5)

    "be initialized properly after creation" in {
      game.users shouldBe empty
      game.field.height shouldBe 7
      game.field.width shouldBe 5
      game.status shouldBe WaitingForUsers
    }

    val user1 = UUID.randomUUID()
    val gameWithOneUser = game.addUser(user1)

    "be able to handle addition of the first user" in {
      gameWithOneUser.status shouldBe WaitingForUsers
      gameWithOneUser.users shouldBe List(user1)
    }

    val user2 = UUID.randomUUID()
    val gameWithUsers = gameWithOneUser.addUser(user2)

    "be able to handle addition of the second user" in {
      gameWithUsers.status shouldBe Active(PlayerNumber.First)
      gameWithUsers.users shouldBe List(user1, user2)
    }

    val move1 = MoveAttempt(2, 2, user1)
    val gameAfterMove1 = gameWithUsers.makeMove(move1)

    "be able to handle correct moves" in {
      inside(gameAfterMove1) {
        case Right(g) =>
          g.status shouldBe Active(PlayerNumber.Second)
          g.field.get(2, 2) shouldBe Some(PlayerNumber.First)

      }
    }

    "be able to handle incorrect moves" in {
      val testSet = List[(Game, MoveAttempt, MoveError)](
        (gameWithOneUser, MoveAttempt(2, 5, user1), GameNotStarted),
        (gameWithUsers, MoveAttempt(-1, 2, user2), ImpossibleMove("smth")),
        (gameWithUsers, MoveAttempt(2, 2, user2), ImpossibleMove("smth")),
        (gameAfterMove1.right.get, MoveAttempt(2, 3, user1), ImpossibleMove("smth"))
      )

      forAll(testSet) {
        case (gameUnderTest, move, result) =>
          inside(gameUnderTest.makeMove(move)) {
            case Left(err) => err.getClass shouldBe result.getClass
          }
      }
    }

    "sets the correct state when one of the players leave before finish" in {
      pending
    }
  }

  "Game winner logic" should {
    val game = Game.create(5, 5, 3)
    "be able to figure out the end of the game" when {
      "when the field is empty" in {
        game.winner() shouldBe None
      }
    }

    val firstUser = UUID.randomUUID()
    val secondUser = UUID.randomUUID()
    val gameWithUsers = game.addUser(firstUser).addUser(secondUser)

    val moveTests =
      List(
        List(
          MoveAttempt(0, 0, firstUser),
          MoveAttempt(0, 1, secondUser),
          MoveAttempt(1, 1, firstUser),
          MoveAttempt(1, 2, secondUser),
          MoveAttempt(2, 2, firstUser)
        ),
        List(
          MoveAttempt(0, 0, firstUser),
          MoveAttempt(1, 1, secondUser),
          MoveAttempt(0, 2, firstUser),
          MoveAttempt(2, 2, secondUser),
          MoveAttempt(0, 4, firstUser),
          MoveAttempt(3, 3, secondUser)
        )
      )

    forAll(moveTests) { moves =>
      val lastMove = moves.last

      val gameAfterMoves = moves.take(moves.size - 1).foldLeft(gameWithUsers)((game, move) => game.makeMove(move).right.get)

      "the game shouldn't be finished when it is not" in {
        gameAfterMoves.winner() shouldBe None
      }

      val finishedGame = gameAfterMoves.makeMove(lastMove).right.get

      "the game should be finished after last move" in {
        finishedGame.winner() shouldBe Some(PlayerNumber.First)
      }
    }
  }
}
