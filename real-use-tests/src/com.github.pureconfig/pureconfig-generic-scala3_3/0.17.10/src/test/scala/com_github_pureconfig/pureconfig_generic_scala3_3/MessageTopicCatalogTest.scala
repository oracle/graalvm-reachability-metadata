/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.jdk.DurationConverters.*

import com.typesafe.config.{Config, ConfigObject, ConfigRenderOptions, ConfigValue, ConfigValueType}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue, fail}
import org.junit.jupiter.api.Test
import pureconfig.{ConfigReader, ConfigSource, ConfigWriter}
import pureconfig.error.{CannotConvert, ConfigReaderFailures, ConvertFailure, KeyNotFound, WrongType}
import pureconfig.generic.semiauto.{deriveReader, deriveWriter}

/**
 * Exercises value-class derivation: `AnyVal` wrappers in a message broker's topic catalog are read
 * from and written to the bare value they wrap (a string, a number, a duration, a list, an optional
 * number or a whole object) by derived readers and writers, both as explicitly derived givens and
 * as fields that case-class and coproduct derivation derive inline, contrasted with a one-field case
 * class that stays an object, rendered back and reloaded unchanged, with the failures an operator
 * sees reported at the wrapped values' paths.
 */
class MessageTopicCatalogTest {
  import MessageTopicCatalogTest.*
  import MessageTopicCatalogTest.given

  @Test
  def loadsTopicsWhoseSettingsAreValueClasses(): Unit = {
    val catalog: TopicCatalog = loadCatalog(CatalogConfig)

    assertEquals(new BrokerList(List("kafka-1:9092", "kafka-2:9092", "kafka-3:9092")), catalog.bootstrap)
    assertEquals(new Retention(7.days), catalog.defaultRetention)
    assertEquals(
      List(
        Topic(
          name = new TopicName("orders.v1"),
          partitions = new PartitionCount(12),
          replication = ReplicationFactor(3),
          cleanup = CleanupPolicy.Delete(new Retention(72.hours)),
          owner = new Owner(Contact("checkout", "checkout@example.com")),
          maxMessageBytes = new MaxMessageBytes(Some(2097152L))
        ),
        Topic(
          name = new TopicName("customer-profiles"),
          partitions = new PartitionCount(6),
          replication = ReplicationFactor(2),
          cleanup = CleanupPolicy.Compact(new Retention(10.minutes)),
          owner = new Owner(Contact("crm", "crm@example.com"))
        )
      ),
      catalog.topics
    )
    assertEquals(new MaxMessageBytes(None), catalog.topics(1).maxMessageBytes)
    assertEquals(
      Map("orders" -> new TopicName("orders.v1"), "profiles" -> new TopicName("customer-profiles")),
      catalog.aliases
    )

    val totalPartitions: Int = catalog.topics.map(_.partitions.value).sum
    assertEquals(18, totalPartitions)
    assertEquals(Some("checkout@example.com"), catalog.ownerOf(new TopicName("orders.v1")).map(_.contact.email))
  }

  @Test
  def rendersValueClassesAsTheValuesTheyWrapAndReloadsUnchanged(): Unit = {
    val catalog: TopicCatalog = loadCatalog(CatalogConfig)
    val rendered: ConfigObject = ConfigWriter[TopicCatalog].to(catalog).asInstanceOf[ConfigObject]
    val renderedConfig: Config = rendered.toConfig

    assertEquals(
      List("kafka-1:9092", "kafka-2:9092", "kafka-3:9092"),
      renderedConfig.getStringList("bootstrap").asScala.toList
    )
    assertEquals(7.days.toJava, renderedConfig.getDuration("default-retention"))
    assertEquals("orders.v1", renderedConfig.getString("aliases.orders"))

    val orders: Config = renderedConfig.getConfigList("topics").get(0)
    assertEquals(ConfigValueType.STRING, orders.getValue("name").valueType)
    assertEquals("orders.v1", orders.getString("name"))
    assertEquals(ConfigValueType.NUMBER, orders.getValue("partitions").valueType)
    assertEquals(12, orders.getInt("partitions"))
    assertEquals(2097152L, orders.getLong("max-message-bytes"))
    assertEquals("delete", orders.getString("cleanup.type"))
    assertEquals(72.hours.toJava, orders.getDuration("cleanup.retention"))

    // The owner wraps a case class, so its value is the contact object itself, not a nested field.
    assertEquals(ConfigValueType.OBJECT, orders.getValue("owner").valueType)
    assertEquals(Set("team", "email"), orders.getConfig("owner").root.keySet.asScala.toSet)
    // A one-field case class is not a value class and keeps its field name.
    assertEquals(ConfigValueType.OBJECT, orders.getValue("replication").valueType)
    assertEquals(3, orders.getInt("replication.value"))

    val reloaded: TopicCatalog =
      ConfigSource.string(rendered.render(ConfigRenderOptions.concise())).loadOrThrow[TopicCatalog]
    assertEquals(catalog, reloaded)
  }

  @Test
  def readsAndWritesStandaloneValueClassesThroughDerivedInstances(): Unit = {
    val name: TopicName =
      ConfigSource.string("""dead-letter = "orders.v1.dlq"""").at("dead-letter").loadOrThrow[TopicName]
    assertEquals(new TopicName("orders.v1.dlq"), name)

    val partitions: ConfigValue = ConfigWriter[PartitionCount].to(new PartitionCount(24))
    assertEquals(ConfigValueType.NUMBER, partitions.valueType)
    assertEquals(24, partitions.unwrapped)

    val owner: Owner = new Owner(Contact("platform", "platform@example.com"))
    val ownerValue: ConfigValue = ConfigWriter[Owner].to(owner)
    assertEquals(ConfigValueType.OBJECT, ownerValue.valueType)
    assertEquals(
      owner,
      ConfigReader[Owner].from(ownerValue).fold(failures => fail(failures.prettyPrint()), identity)
    )

    val stringly: ConfigValue = ConfigSource.string("partitions = \"48\"").config()
      .fold(failures => fail(failures.prettyPrint()), identity)
      .getValue("partitions")
    assertEquals(
      new PartitionCount(48),
      ConfigReader[PartitionCount].from(stringly).fold(failures => fail(failures.prettyPrint()), identity)
    )
  }

  @Test
  def reportsMisconfiguredWrappedValuesAtTheirPaths(): Unit = {
    val brokenConfig: String = """
      catalog {
        bootstrap = "kafka-1:9092"
        default-retention = 7 days
        topics = [
          {
            name = "orders.v1"
            partitions = many
            replication = 3
            cleanup { type = delete, retention = "forever-ish" }
            owner { team = checkout }
          }
        ]
        aliases { orders = [ "orders.v1" ] }
      }
      """

    ConfigSource.string(brokenConfig).at("catalog").load[TopicCatalog] match {
      case Right(catalog) => fail(s"Expected the broken topic catalog to be rejected, but loaded $catalog")
      case Left(failures) =>
        val reasons: Map[String, Any] = failures.toList.collect {
          case ConvertFailure(reason, _, path) => path -> reason
        }.toMap
        assertEquals(
          Set(
            "catalog.bootstrap",
            "catalog.topics.0.partitions",
            "catalog.topics.0.replication",
            "catalog.topics.0.cleanup.retention",
            "catalog.topics.0.owner",
            "catalog.aliases.orders"
          ),
          reasons.keySet,
          failures.prettyPrint()
        )
        assertWrongType(reasons("catalog.bootstrap"), ConfigValueType.STRING, ConfigValueType.LIST, failures)
        assertWrongType(
          reasons("catalog.topics.0.replication"),
          ConfigValueType.NUMBER,
          ConfigValueType.OBJECT,
          failures
        )
        assertWrongType(reasons("catalog.aliases.orders"), ConfigValueType.LIST, ConfigValueType.STRING, failures)
        assertWrongType(
          reasons("catalog.topics.0.partitions"),
          ConfigValueType.STRING,
          ConfigValueType.NUMBER,
          failures
        )
        assertTrue(
          reasons("catalog.topics.0.cleanup.retention") match {
            case CannotConvert("forever-ish", "Duration", _) => true
            case _ => false
          },
          failures.prettyPrint()
        )
        assertTrue(
          reasons("catalog.topics.0.owner") match {
            case KeyNotFound("email", _) => true
            case _ => false
          },
          failures.prettyPrint()
        )
    }
  }

  private def assertWrongType(
      reason: Any,
      found: ConfigValueType,
      expected: ConfigValueType,
      failures: ConfigReaderFailures
  ): Unit =
    reason match {
      case WrongType(actual, expectedTypes) =>
        assertEquals(found, actual, failures.prettyPrint())
        assertTrue(expectedTypes.contains(expected), failures.prettyPrint())
      case other => fail(s"Expected a WrongType failure but got $other: ${failures.prettyPrint()}")
    }

  private def loadCatalog(text: String): TopicCatalog =
    ConfigSource.string(text).at("catalog").load[TopicCatalog] match {
      case Right(catalog) => catalog
      case Left(failures) => fail(s"The topic catalog does not load: ${failures.prettyPrint()}")
    }
}

object MessageTopicCatalogTest {
  final class TopicName(val value: String) extends AnyVal {
    override def toString: String = s"TopicName($value)"
  }

  given ConfigReader[TopicName] = deriveReader[TopicName]
  given ConfigWriter[TopicName] = deriveWriter[TopicName]

  final class PartitionCount(val value: Int) extends AnyVal {
    override def toString: String = s"PartitionCount($value)"
  }

  given ConfigReader[PartitionCount] = deriveReader[PartitionCount]
  given ConfigWriter[PartitionCount] = deriveWriter[PartitionCount]

  final case class Contact(team: String, email: String)

  given ConfigReader[Contact] = deriveReader[Contact]
  given ConfigWriter[Contact] = deriveWriter[Contact]

  /** Wraps a whole object, so the owner is written as the contact's fields. */
  final class Owner(val contact: Contact) extends AnyVal {
    override def toString: String = s"Owner($contact)"
  }

  given ConfigReader[Owner] = deriveReader[Owner]
  given ConfigWriter[Owner] = deriveWriter[Owner]

  /** The following wrappers have no given instances and are derived along with their owners. */
  final class Retention(val period: FiniteDuration) extends AnyVal {
    override def toString: String = s"Retention($period)"
  }

  final class BrokerList(val hosts: List[String]) extends AnyVal {
    override def toString: String = s"BrokerList($hosts)"
  }

  final class MaxMessageBytes(val limit: Option[Long]) extends AnyVal {
    override def toString: String = s"MaxMessageBytes($limit)"
  }

  /** Not a value class: a one-field case class stays an object with a `value` key. */
  final case class ReplicationFactor(value: Int)

  given ConfigReader[ReplicationFactor] = deriveReader[ReplicationFactor]
  given ConfigWriter[ReplicationFactor] = deriveWriter[ReplicationFactor]

  sealed trait CleanupPolicy

  object CleanupPolicy {
    final case class Delete(retention: Retention) extends CleanupPolicy
    final case class Compact(minCompactionLag: Retention) extends CleanupPolicy
  }

  given ConfigReader[CleanupPolicy] = deriveReader[CleanupPolicy]
  given ConfigWriter[CleanupPolicy] = deriveWriter[CleanupPolicy]

  final case class Topic(
      name: TopicName,
      partitions: PartitionCount,
      replication: ReplicationFactor,
      cleanup: CleanupPolicy,
      owner: Owner,
      maxMessageBytes: MaxMessageBytes = new MaxMessageBytes(None)
  )

  given ConfigReader[Topic] = deriveReader[Topic]
  given ConfigWriter[Topic] = deriveWriter[Topic]

  final case class TopicCatalog(
      bootstrap: BrokerList,
      defaultRetention: Retention,
      topics: List[Topic],
      aliases: Map[String, TopicName]
  ) {
    def ownerOf(name: TopicName): Option[Owner] = topics.find(_.name == name).map(_.owner)
  }

  given ConfigReader[TopicCatalog] = deriveReader[TopicCatalog]
  given ConfigWriter[TopicCatalog] = deriveWriter[TopicCatalog]

  val CatalogConfig: String = """
    catalog {
      bootstrap = ["kafka-1:9092", "kafka-2:9092", "kafka-3:9092"]
      default-retention = 7 days
      topics = [
        {
          name = "orders.v1"
          partitions = 12
          replication { value = 3 }
          cleanup { type = delete, retention = 72 hours }
          owner { team = checkout, email = "checkout@example.com" }
          max-message-bytes = 2097152
        }
        {
          name = customer-profiles
          partitions = 6
          replication { value = 2 }
          cleanup { type = compact, min-compaction-lag = 10 minutes }
          owner { team = crm, email = "crm@example.com" }
        }
      ]
      aliases {
        orders = "orders.v1"
        profiles = customer-profiles
      }
    }
    """
}
