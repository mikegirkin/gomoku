package net.girkin.gomoku.api

import net.girkin.gomoku.game._
import io.circe.generic.extras.semiauto._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.syntax._

object ApiObjects {

  private implicit val config: Configuration = Configuration.default.withDefaults.withDiscriminator("type")

  implicit val playerNumberCodec: Codec[PlayerNumber] = Codec.from(
    Decoder.decodeInt.emapTry(PlayerNumber.fromInt),
    Encoder.instance[PlayerNumber] { v =>
      Json.fromInt(v.asInt)
    }
  )

  implicit val gameFinishReasonCodec: Codec[GameFinishReason] = deriveConfiguredCodec[GameFinishReason]
  implicit val gameStatusCodec: Codec[GameStatus] = deriveConfiguredCodec[GameStatus]
  implicit val moveAttemptCodec: Codec[MoveAttempt] = deriveConfiguredCodec[MoveAttempt]

  implicit val gomokuRequestCodec: Codec[GomokuGameRequest] = deriveConfiguredCodec[GomokuGameRequest]


  implicit val gameFieldEncoder: Encoder[GameField] = deriveConfiguredEncoder[GameField]
  implicit val gameEncoder: Encoder[Game] = deriveConfiguredEncoder[Game]

  implicit val joinGameSuccessCodec: Codec[JoinGameSuccess] = deriveConfiguredCodec[JoinGameSuccess]
  implicit val joinGameErrorCodec: Codec[JoinGameError] = deriveConfiguredCodec[JoinGameError]
  implicit val leaveGameErrorCodec: Codec[LeaveGameError] = deriveConfiguredCodec[LeaveGameError]
  implicit val gameStateChangedCodec: Codec[GameStateChanged] = deriveConfiguredCodec[GameStateChanged]

  implicit val incomingGameMessageCodec: Codec[IncomingGameMessage] = deriveConfiguredCodec[IncomingGameMessage]

}
