package net.girkin.gomoku.api

import net.girkin.gomoku.game._
import io.circe.generic.semiauto._
import io.circe._
import io.circe.syntax._

object ApiObjects {

  implicit val playerNumberEncoder = Encoder.instance[PlayerNumber] { v =>
    Json.fromInt(v.asInt)
  }

  implicit val gameFinishReasonEncoder = Encoder.instance[GameFinishReason] {
    case Draw => Json.obj(
      "_t" -> Json.fromString("Draw")
    )
    case PlayerQuit(playerNumber) => Json.obj(
      "_t" -> Json.fromString("PlayerQuit"),
      "player" -> playerNumber.asJson
    )
    case PlayerWon(playerNumber) => Json.obj(
      "_t" -> Json.fromString("PlayerWon"),
      "player" -> playerNumber.asJson
    )
  }

  implicit val gameStatusEncoder = Encoder.instance[GameStatus] {
    case WaitingForUsers => Json.obj("_t" -> Json.fromString("WaitingForUsers"))
    case Active(awaitingMoveFrom) => Json.obj(
      "_t" -> Json.fromString("Active"),
      "awaitingMoveFrom" -> awaitingMoveFrom.asJson
    )
    case Finished(reason) => Json.obj(
      "_t" -> Json.fromString("Finished"),
      "reason" -> reason.asJson
    )
  }

  implicit val gameFieldEncoder = deriveEncoder[GameField]

  implicit val gameEncoder = deriveEncoder[Game]

  implicit val joinGameSuccessEncoder = deriveEncoder[JoinGameSuccess]
  implicit val joinGameErrorEncoder = deriveEncoder[JoinGameError]

}
