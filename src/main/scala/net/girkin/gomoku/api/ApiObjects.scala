package net.girkin.gomoku.api

import net.girkin.gomoku.game._
import io.circe.generic.extras.semiauto._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.syntax._

object ApiObjects {

  private implicit val config: Configuration = Configuration.default.withDefaults.withDiscriminator("type")

  implicit val playerNumberEncoder = Codec.from(
    Decoder.decodeInt.emapTry(PlayerNumber.fromInt),
    Encoder.instance[PlayerNumber] { v =>
      Json.fromInt(v.asInt)
    }
  )

  implicit val gameFinishReasonCodec: Codec[GameFinishReason] = deriveConfiguredCodec[GameFinishReason]

  implicit val gameStatusEncoder = Encoder.instance[GameStatus] {
    case WaitingForUsers => Json.obj(
      "type" -> Json.fromString("WaitingForUsers")
    )
    case Active(awaitingMoveFrom) => Json.obj(
      "type" -> Json.fromString("Active"),
      "awaitingMoveFrom" -> awaitingMoveFrom.asJson
    )
    case Finished(reason) => Json.obj(
      "type" -> Json.fromString("Finished"),
      "reason" -> reason.asJson
    )
  }

  implicit val gameStatusDecoder = ???
  //  Decoder.instance[GameStatus] { cursor =>
//    for {
//      `type` <- cursor.get[String]("type")
//      result <- `type` match {
//        case "WaitingForUsers" => WaitingForUsers
//        case "Active" => {
//          cursor.get[Int]("awaitingMoveFrom").map { playerNumber =>
//            Active(playerNumber)
//          }
//        }
//        case "Finished" => {
//          cursor.get[GameFinishReason].
//        }
//      }
//    } yield {
//      result
//    }
//  }

  implicit val gameFieldEncoder = deriveConfiguredEncoder[GameField]
  implicit val gameEncoder = deriveConfiguredEncoder[Game]

  implicit val joinGameSuccessEncoder = deriveConfiguredEncoder[JoinGameSuccess]
  implicit val joinGameErrorEncoder = deriveConfiguredEncoder[JoinGameError]

}
