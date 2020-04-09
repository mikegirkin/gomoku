package net.girkin.gomoku.api

import io.circe.syntax._
import net.girkin.gomoku.game._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ApiObjectsSpec extends AnyWordSpec
  with Matchers {

  import ApiObjects._

  "GameFinishReason is serializable" in {
    val reason: GameFinishReason = PlayerQuit(PlayerNumber.First)
    val json = reason.asJson
    val cursor = json.hcursor

    cursor.get[String]("type") shouldBe Right(classOf[PlayerQuit].getSimpleName)
    cursor.get[Int]("playerNumber") shouldBe Right(PlayerNumber.First.asInt)

    val decoded = json.as[GameFinishReason]

    decoded shouldBe Right(reason)
  }

  "PlayerNumber is serializable" in {
    val number: PlayerNumber = PlayerNumber.First
    val json = number.asJson
    val cursor = json.hcursor

    cursor.focus.get.as[Int] shouldBe Right(PlayerNumber.First.asInt)

    val decoded = json.as[PlayerNumber]

    decoded shouldBe Right(number)
  }

  "GameStatus is serializable" in {
    val status: GameStatus = Active(PlayerNumber.Second)
    val json = status.asJson

    val cursor = json.hcursor
    cursor.get[String]("type") shouldBe Right(classOf[Active].getSimpleName)
    cursor.get[Int]("awaitingMoveFrom") shouldBe Right(PlayerNumber.Second.asInt)

    val decoded = json.as[GameStatus]
    decoded shouldBe Right(status)
  }

}
