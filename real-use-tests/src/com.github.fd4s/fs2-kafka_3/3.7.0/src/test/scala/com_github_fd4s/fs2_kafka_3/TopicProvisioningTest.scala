/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_fd4s.fs2_kafka_3

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.kafka.AdminClientSettings
import fs2.kafka.KafkaAdminClient
import fs2.kafka.admin.MkAdminClient
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.AlterConfigOp
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.MockAdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.TopicDescription
import org.apache.kafka.common.Node
import org.apache.kafka.common.TopicPartitionInfo
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.config.TopicConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Provisions an application's topic layout through the fs2-kafka admin client: listing,
 * creating, reconfiguring, describing and deleting topics and describing the cluster.
 *
 * The Kafka admin client underneath is kafka-clients' `MockAdminClient`, plugged in through
 * the `MkAdminClient` extension point, so the cluster state is in memory and deterministic.
 */
class TopicProvisioningTest {

  import TopicProvisioningTest.*

  @Test
  def provisionerBringsTheClusterToTheDesiredTopicLayout(): Unit = {
    val cluster: SeededCluster = new SeededCluster()
    given MkAdminClient[IO] = cluster.mkAdminClient

    val (undersized, report): (List[String], ProvisioningReport) = KafkaAdminClient
      .resource[IO](adminSettings)
      .use(admin => provision(admin, DesiredLayout, Retired).product(inspect(admin)))
      .unsafeRunSync()

    assertEquals(Set(PaymentsTopic, InvoicesTopic, RefundsTopic), report.applicationTopics)
    assertEquals(report.applicationTopics + InternalTopic, report.allTopics,
      "internal topics are listed on request and flagged as internal")
    assertFalse(report.applicationTopics.contains(LegacyTopic), "the retired topic is deleted")

    assertEquals(List(PaymentsTopic), undersized, "payments has fewer partitions than specified")
    assertEquals(2, report.descriptions(PaymentsTopic).partitions.size,
      "existing topics are never repartitioned, which would reshuffle keys")
    assertEquals(3, report.descriptions(InvoicesTopic).partitions.size)
    assertEquals(1, report.descriptions(RefundsTopic).partitions.size)
    report.descriptions.values.foreach(description => assertFalse(description.isInternal))
    val invoicePartition: TopicPartitionInfo = report.descriptions(InvoicesTopic).partitions.get(0)
    assertEquals(2, invoicePartition.replicas.size, "new topics use the requested replication factor")

    assertEquals(Some("604800000"), report.config(PaymentsTopic, TopicConfig.RETENTION_MS_CONFIG))
    assertEquals(Some("compact"), report.config(InvoicesTopic, TopicConfig.CLEANUP_POLICY_CONFIG))
    assertEquals(Some("86400000"), report.config(RefundsTopic, TopicConfig.RETENTION_MS_CONFIG))
    assertEquals(None, report.config(PaymentsTopic, TopicConfig.MAX_MESSAGE_BYTES_CONFIG),
      "the obsolete override on payments is removed")

    assertEquals("payments-cluster", report.clusterId)
    assertEquals(3, report.brokers.size)
    assertTrue(report.brokers.map(_.id).contains(report.controller.id))
    assertEquals("topic-provisioner", cluster.lastSettings(AdminClientConfig.CLIENT_ID_CONFIG))
    assertEquals("localhost:9092", cluster.lastSettings(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG))
  }

  @Test
  def provisioningTwiceLeavesTheLayoutUnchanged(): Unit = {
    val cluster: SeededCluster = new SeededCluster()
    given MkAdminClient[IO] = cluster.mkAdminClient

    val (first, second): (ProvisioningReport, ProvisioningReport) = KafkaAdminClient
      .resource[IO](adminSettings)
      .use { admin =>
        for {
          first <- provision(admin, DesiredLayout, Retired) *> inspect(admin)
          undersized <- provision(admin, DesiredLayout, Retired)
          second <- inspect(admin)
        } yield {
          assertEquals(List(PaymentsTopic), undersized)
          (first, second)
        }
      }
      .unsafeRunSync()

    assertEquals(first.applicationTopics, second.applicationTopics)
    assertEquals(
      first.descriptions.view.mapValues(_.partitions.size).toMap,
      second.descriptions.view.mapValues(_.partitions.size).toMap
    )
    assertEquals(first.configs, second.configs)
  }
}

object TopicProvisioningTest {

  val PaymentsTopic: String = "payments"
  val InvoicesTopic: String = "invoices"
  val RefundsTopic: String = "refunds"
  val LegacyTopic: String = "payments-legacy"
  val InternalTopic: String = "__consumer_offsets"

  /** The topic layout the payments service needs, as the operations team specified it. */
  final case class TopicSpec(name: String, partitions: Int, replicationFactor: Short, config: Map[String, String])

  val DesiredLayout: List[TopicSpec] = List(
    TopicSpec(PaymentsTopic, 6, 2, Map(TopicConfig.RETENTION_MS_CONFIG -> "604800000")),
    TopicSpec(InvoicesTopic, 3, 2, Map(TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_COMPACT)),
    TopicSpec(RefundsTopic, 1, 1, Map(TopicConfig.RETENTION_MS_CONFIG -> "86400000"))
  )

  val Retired: List[String] = List(LegacyTopic)

  /** Overrides an older deployment set and the current layout no longer wants. */
  val ObsoleteOverrides: List[String] = List(TopicConfig.MAX_MESSAGE_BYTES_CONFIG)

  val adminSettings: AdminClientSettings =
    AdminClientSettings("localhost:9092")
      .withClientId("topic-provisioner")
      .withRequestTimeout(5.seconds)

  final case class ProvisioningReport(
      allTopics: Set[String],
      applicationTopics: Set[String],
      descriptions: Map[String, TopicDescription],
      configs: Map[String, Map[String, String]],
      clusterId: String,
      brokers: Set[Node],
      controller: Node
  ) {
    def config(topic: String, key: String): Option[String] = configs(topic).get(key)
  }

  /**
   * Creates missing topics, applies each topic's config overrides, drops obsolete overrides
   * and deletes retired topics. Returns the existing topics with fewer partitions than
   * specified, which operators repartition by hand because that reshuffles keys.
   */
  def provision(admin: KafkaAdminClient[IO], layout: List[TopicSpec], retired: List[String]): IO[List[String]] =
    for {
      existing <- admin.listTopics.names
      missing = layout.filterNot(spec => existing.contains(spec.name))
      _ <- admin.createTopics(missing.map { spec =>
        new NewTopic(spec.name, spec.partitions, spec.replicationFactor).configs(spec.config.asJava)
      }).whenA(missing.nonEmpty)
      present = layout.filter(spec => existing.contains(spec.name))
      current <- admin.describeTopics(present.map(_.name))
      _ <- admin.alterConfigs(present.map { spec =>
        topicResource(spec.name) -> (
          spec.config.toList.map((key, value) => configOp(key, value, AlterConfigOp.OpType.SET)) ++
            ObsoleteOverrides.map(key => configOp(key, "", AlterConfigOp.OpType.DELETE))
        )
      }.toMap).whenA(present.nonEmpty)
      stale = retired.filter(existing.contains)
      _ <- admin.deleteTopics(stale).whenA(stale.nonEmpty)
    } yield present.filter(spec => current(spec.name).partitions.size < spec.partitions).map(_.name)

  /** Reads the cluster back the way an operator dashboard would. */
  def inspect(admin: KafkaAdminClient[IO]): IO[ProvisioningReport] =
    for {
      listings <- admin.listTopics.includeInternal.namesToListings
      allTopics = listings.keySet
      applicationTopics = listings.filterNot((_, listing) => listing.isInternal).keySet
      descriptions <- admin.describeTopics(applicationTopics.toList)
      configEntries <- admin.describeConfigs(applicationTopics.toList.map(topicResource))
      clusterId <- admin.describeCluster.clusterId
      brokers <- admin.describeCluster.nodes
      controller <- admin.describeCluster.controller
    } yield ProvisioningReport(
      allTopics,
      applicationTopics,
      descriptions,
      configEntries.map((resource, entries) => resource.name -> entries.map(entry => entry.name -> entry.value).toMap),
      clusterId,
      brokers,
      controller
    )

  def configOp(key: String, value: String, opType: AlterConfigOp.OpType): AlterConfigOp =
    new AlterConfigOp(new ConfigEntry(key, value), opType)

  def topicResource(topic: String): ConfigResource = new ConfigResource(ConfigResource.Type.TOPIC, topic)

  /**
   * Stands in for a three-broker cluster an older deployment already provisioned: payments
   * exists with too few partitions and an obsolete override, next to a retired topic and the
   * internal offsets topic.
   */
  final class SeededCluster {

    private val admin: MockAdminClient =
      MockAdminClient.create().numBrokers(3).controller(1).clusterId("payments-cluster").build()

    private val broker: Node = admin.brokers.get(0)

    @volatile private var settings: Map[String, String] = Map.empty

    seed(PaymentsTopic, 2, internal = false, Map(TopicConfig.MAX_MESSAGE_BYTES_CONFIG -> "2097152"))
    seed(LegacyTopic, 1, internal = false, Map.empty)
    seed(InternalTopic, 1, internal = true, Map(TopicConfig.CLEANUP_POLICY_CONFIG -> "compact"))

    private def seed(topic: String, partitions: Int, internal: Boolean, config: Map[String, String]): Unit =
      admin.addTopic(
        internal,
        topic,
        (0 until partitions).map { partition =>
          new TopicPartitionInfo(partition, broker, List(broker).asJava, List(broker).asJava)
        }.asJava,
        config.asJava
      )

    val mkAdminClient: MkAdminClient[IO] = new MkAdminClient[IO] {
      def apply(adminSettings: AdminClientSettings): IO[AdminClient] =
        IO {
          settings = adminSettings.properties
          admin
        }
    }

    def lastSettings: Map[String, String] = settings
  }
}
