package net.girkin.gomoku.game

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GameConciergeSpec extends AnyWordSpec with Matchers with MockFactory {

  "GameConcierge" should {
    "return JoinedQueue" in {

      val concierge = new GameConciergeImpl(gameStore, gameStreams, playerQueue)
    }
  }

}
