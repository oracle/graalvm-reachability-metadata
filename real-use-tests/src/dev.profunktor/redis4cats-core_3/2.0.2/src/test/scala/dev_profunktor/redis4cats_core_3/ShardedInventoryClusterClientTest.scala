/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import com.github.fppt.jedismock.RedisServer
import com.github.fppt.jedismock.server.ServiceOptions
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.config.NoRefresh
import dev.profunktor.redis4cats.config.Redis4CatsConfig
import dev.profunktor.redis4cats.connection.OperationNotSupported
import dev.profunktor.redis4cats.connection.RedisClusterClient
import dev.profunktor.redis4cats.connection.RedisURI
import dev.profunktor.redis4cats.data.NodeId
import dev.profunktor.redis4cats.data.ReadFrom
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import io.lettuce.core.cluster.models.partitions.Partitions
import io.lettuce.core.cluster.models.partitions.RedisClusterNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.jdk.CollectionConverters.*

/**
 * Connects to a Redis Cluster through `RedisClusterClient`, its topology view and node-pinned connections.
 *
 * An inventory service shards stock counters across a Redis Cluster. It
 * builds the cluster client from seed URIs with a node filter that keeps only
 * masters and no background topology refresh, then inspects the discovered
 * partitions to see which node owns the hash slots. Stock levels spanning
 * different slots are written and read in one call on the cluster-wide
 * connection, which refuses transactions because they cannot span nodes.
 * Resolving the owning node's ID from its URL gives a connection pinned to
 * that node, where a restock transaction does run.
 */
class ShardedInventoryClusterClientTest {

  /** What the inventory service observes about the cluster and its data. */
  private final case class Observed(
      nodes: List[RedisClusterNode],
      ownerId: NodeId,
      stock: Map[String, String],
      clusterTransaction: Either[Throwable, Unit],
      restocked: Option[String],
      seenClusterWide: Option[String]
  )

  @Test
  def shardsStockAcrossTheClusterAndPinsTransactionsToANode(): Unit = {
    val server: RedisServer =
      RedisServer.newRedisServer().setOptions(ServiceOptions.defaultOptions().withClusterModeEnabled()).start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("sharded-inventory")
      given Log[IO] = log4CatsInstance[IO]

      val nodeUrl: String = s"redis://${server.getHost}:${server.getBindPort}"
      val config: Redis4CatsConfig = Redis4CatsConfig()
        .withTopologyViewRefreshStrategy(NoRefresh)
        .withNodeFilter(node => node.is(RedisClusterNode.NodeFlag.UPSTREAM))

      val cluster: Resource[IO, RedisClusterClient] =
        for {
          seed <- Resource.eval(RedisURI.make[IO](nodeUrl))
          client <- RedisClusterClient.configured[IO](config, seed)
        } yield client

      val stockKeys: List[String] = List("stock:widget", "stock:gadget", "stock:sprocket")

      val observed: Observed = cluster
        .use { client =>
          val inventory: Resource[IO, RedisCommands[IO, String, String]] =
            Redis[IO].fromClusterClient(client, RedisCodec.Utf8)(Some(ReadFrom.UpstreamPreferred))
          for {
            partitions <- RedisClusterClient.partitions[IO](client)
            ownerId <- RedisClusterClient.nodeId[IO](client, nodeUrl)
            clusterWide <- inventory.use { redis =>
              for {
                _ <- redis.mSet(Map("stock:widget" -> "12", "stock:gadget" -> "3", "stock:sprocket" -> "40"))
                stock <- redis.mGet(stockKeys.toSet)
                attempt <- redis.transact_(List(redis.incrBy("stock:gadget", 10L).void)).attempt
              } yield (stock, attempt)
            }
            restocked <- Redis[IO].fromClusterClientByNode(client, RedisCodec.Utf8, ownerId)().use { node =>
              node.transact_(List(node.incrBy("stock:gadget", 10L).void, node.decrBy("stock:widget", 2L).void)) *>
                node.get("stock:gadget")
            }
            seen <- inventory.use(_.get("stock:widget"))
          } yield Observed(nodes(partitions), ownerId, clusterWide._1, clusterWide._2, restocked, seen)
        }
        .unsafeRunSync()

      assertEquals(1, observed.nodes.size, observed.nodes.toString)
      val owner: RedisClusterNode = observed.nodes.head
      assertEquals(owner.getNodeId, observed.ownerId.value)
      assertEquals(server.getBindPort, owner.getUri.getPort)
      assertTrue(owner.is(RedisClusterNode.NodeFlag.UPSTREAM), owner.getFlags.toString)
      assertEquals(16384, owner.getSlots.size)

      assertEquals(Map("stock:widget" -> "12", "stock:gadget" -> "3", "stock:sprocket" -> "40"), observed.stock)
      assertTrue(
        observed.clusterTransaction.left.exists(_.isInstanceOf[OperationNotSupported]),
        s"expected transactions to be refused cluster-wide, got ${observed.clusterTransaction}"
      )
      assertEquals(Some("13"), observed.restocked)
      assertEquals(Some("10"), observed.seenClusterWide)
    } finally {
      server.stop()
    }
  }

  /** Lists the nodes of the client's topology view. */
  private def nodes(partitions: Partitions): List[RedisClusterNode] = partitions.asScala.toList
}
