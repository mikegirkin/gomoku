package net.girkin.gomoku.api

import io.circe.syntax._
import net.girkin.gomoku.game.{GameFinished, PlayerNumber, PlayerQuit}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec


class ApiObjectsSpec extends AnyWordSpec
  with Matchers {

  import ApiObjects._

  "GameFinishReason" should {
    "be serializable" in {

      val reason = PlayerQuit(PlayerNumber.First)

      val json = reason.asJson

      json \\ "type" shouldBe classOf[PlayerQuit].getSimpleName
      json \\ "playerNumber" shouldBe PlayerNumber.First.asInt
    }
  }

}
