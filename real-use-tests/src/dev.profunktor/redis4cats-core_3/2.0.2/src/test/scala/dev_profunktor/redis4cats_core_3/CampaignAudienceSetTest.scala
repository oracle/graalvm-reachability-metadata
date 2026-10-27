/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.NoOp._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import redis.embedded.RedisServer

import java.net.ServerSocket
import scala.concurrent.duration._

/** Exercises set-based audience segmentation to publish a deduplicated campaign mailing list. */
class CampaignAudienceSetTest {
  @Test
  @Timeout(60)
  def audienceRefreshHonorsInterestsSuppressionAndUnsubscription(): Unit = {
    val subscribersKey: String = "newsletter:subscribers"
    val interestKey: String = "interests:coffee"
    val suppressionKey: String = "newsletter:suppressed"
    val candidatesKey: String = "campaign:coffee:candidates"
    val audienceKey: String = "campaign:coffee:audience"
    val program: IO[Unit] = serverResource.use { server =>
      val uri: String = s"redis://127.0.0.1:${server.ports().get(0)}?timeout=15s"
      for {
        _ <- Redis[IO].utf8(uri).use { campaigns =>
          for {
            _ <- campaigns.sAdd(subscribersKey, "alice", "bob", "carol", "dave")
            // Reimporting an address must not schedule a second delivery.
            duplicate <- campaigns.sAdd(subscribersKey, "alice")
            _ <- campaigns.sAdd(interestKey, "alice", "carol", "eve")
            _ <- campaigns.sAdd(suppressionKey, "carol")
            candidates <- campaigns.sInterStore(candidatesKey, subscribersKey, interestKey)
            recipients <- campaigns.sDiffStore(audienceKey, candidatesKey, suppressionKey)
            audience <- campaigns.sMembers(audienceKey)
            _ <- IO {
              assertEquals(0L, duplicate)
              assertEquals(2L, candidates)
              assertEquals(1L, recipients)
              assertEquals(Set("alice"), audience)
            }
            // Alice unsubscribes and Bob opts into coffee before the next campaign refresh.
            removed <- campaigns.sRem(subscribersKey, "alice")
            _ <- campaigns.sAdd(interestKey, "bob")
            refreshedCandidates <- campaigns.sInterStore(candidatesKey, subscribersKey, interestKey)
            refreshedRecipients <- campaigns.sDiffStore(audienceKey, candidatesKey, suppressionKey)
            _ <- IO {
              assertEquals(1L, removed)
              assertEquals(2L, refreshedCandidates)
              assertEquals(1L, refreshedRecipients)
            }
          } yield ()
        }
        _ <- Redis[IO].utf8(uri).use { mailer =>
          for {
            audience <- mailer.sMembers(audienceKey)
            deliveryCount <- mailer.sCard(audienceKey)
            subscribers <- mailer.sMembers(subscribersKey)
            interests <- mailer.sMembers(interestKey)
            suppression <- mailer.sMembers(suppressionKey)
            _ <- IO {
              // Refresh replaces the old audience rather than retaining an unsubscribed recipient.
              assertEquals(Set("bob"), audience)
              assertEquals(1L, deliveryCount)
              assertEquals(Set("bob", "carol", "dave"), subscribers)
              assertEquals(Set("alice", "bob", "carol", "eve"), interests)
              assertEquals(Set("carol"), suppression)
            }
          } yield ()
        }
      } yield ()
    }
    program.timeout(45.seconds).unsafeRunSync()
  }

  private def serverResource: Resource[IO, RedisServer] = {
    val create: IO[RedisServer] = IO.blocking {
      val socket: ServerSocket = new ServerSocket(0)
      val port: Int = try socket.getLocalPort finally socket.close()
      RedisServer.newRedisServer()
        .bind("127.0.0.1")
        .port(port)
        .setting("save \"\"")
        .setting("appendonly no")
        .build()
    }
    Resource.make(create)(server => IO.blocking(server.stop()).void)
      .evalTap(server => IO.blocking(server.start()).void)
  }
}
