package net.girkin.gomoku.api

import io.circe.syntax._
import net.girkin.gomoku.game.{GameFinishReason, GameFinished, PlayerNumber, PlayerQuit}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec


class ApiObjectsSpec extends AnyWordSpec
  with Matchers {

  import ApiObjects._

  "GameFinishReason" should {
    "be serializable" in {

      val reason: GameFinishReason = PlayerQuit(PlayerNumber.First)

      val json = reason.asJson

      val cursor = json.hcursor

      cursor.get[String]("type") shouldBe Right(classOf[PlayerQuit].getSimpleName)
      cursor.get[Int]("playerNumber") shouldBe Right(PlayerNumber.First.asInt)
    }
  }

}
