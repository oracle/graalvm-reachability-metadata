/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_fd4s.fs2_kafka_3

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.kafka.*
import fs2.kafka.security.KafkaCredentialStore
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.Metric
import org.apache.kafka.common.MetricName
import org.apache.kafka.common.config.SslConfigs
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Connects an application's producer, consumer and admin client to a mutual-TLS cluster with
 * PEM credentials through `KafkaCredentialStore`, the fs2-kafka security credential store.
 *
 * The invoice service reads its CA certificate, client key and client certificate chain from
 * a secrets bundle, turns them into a credential store with `fromPemStrings` and applies it to
 * every client's settings with `withCredentials`. The clients are the real Kafka clients, so
 * starting them loads the PEM key store and trust store; no broker is needed for that.
 */
class BillingClusterTlsCredentialsTest {

  import BillingClusterTlsCredentialsTest.*

  @Test
  def credentialStoreConfiguresEveryClientForMutualTls(): Unit = {
    val store: KafkaCredentialStore = credentialStore(BillingSecrets)

    val producer: Map[String, String] = producerSettings(store).properties
    val consumer: Map[String, String] = consumerSettings(store).properties
    val admin: Map[String, String] = adminSettings(store).properties

    for (properties <- List(producer, consumer, admin)) {
      assertEquals("SSL", properties(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG))
      assertEquals("PEM", properties(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG))
      assertEquals("PEM", properties(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG))
      assertEquals(singleLine(BillingSecrets.caCertificate), properties(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG))
      assertEquals(singleLine(BillingSecrets.clientKey), properties(SslConfigs.SSL_KEYSTORE_KEY_CONFIG))
      assertEquals(
        singleLine(BillingSecrets.clientCertificateChain),
        properties(SslConfigs.SSL_KEYSTORE_CERTIFICATE_CHAIN_CONFIG)
      )
      assertEquals(BootstrapServers, properties(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG))
    }
    assertEquals(store.properties, producer.view.filterKeys(store.properties.contains).toMap,
      "the store contributes the same properties to every client")
    assertFalse(store.properties.values.exists(_.contains('\n')),
      "multi-line PEM blocks are flattened into single-line property values")

    assertEquals("invoice-publisher", producer(CommonClientConfigs.CLIENT_ID_CONFIG))
    assertEquals("invoice-reader", consumer(CommonClientConfigs.CLIENT_ID_CONFIG))
    assertEquals("billing-invoices", consumer(CommonClientConfigs.GROUP_ID_CONFIG))
    assertEquals("invoice-admin", admin(CommonClientConfigs.CLIENT_ID_CONFIG))
    assertEquals("", producer(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG),
      "a property set after the credentials is kept next to them")
  }

  @Test
  def realClientsStartWithThePemKeyStoreAndTrustStore(): Unit = {
    val store: KafkaCredentialStore = credentialStore(BillingSecrets)

    val (producerMetrics, consumerMetrics, adminStarted): (
        Map[MetricName, Metric],
        Map[MetricName, Metric],
        Boolean
    ) = (for {
      producer <- KafkaProducer.resource(producerSettings(store))
      consumer <- KafkaConsumer.resource(consumerSettings(store))
      _ <- KafkaAdminClient.resource[IO](adminSettings(store))
    } yield (producer, consumer))
      .use { (producer, consumer) =>
        for {
          producerMetrics <- producer.metrics
          consumerMetrics <- consumer.metrics
        } yield (producerMetrics, consumerMetrics, true)
      }
      .unsafeRunSync()

    assertTrue(adminStarted)
    assertEquals(Set("invoice-publisher"), clientIds(producerMetrics),
      "the producer started with the TLS settings and reports under its client id")
    assertEquals(Set("invoice-reader"), clientIds(consumerMetrics),
      "the consumer started with the TLS settings and reports under its client id")
    assertTrue(producerMetrics.keySet.exists(_.group == "producer-metrics"))
    assertTrue(consumerMetrics.keySet.exists(_.group == "consumer-metrics"))
  }

  @Test
  def corruptedClientKeyFailsClientStartupInsteadOfConnectingInPlaintext(): Unit = {
    val corrupted: TlsSecrets = BillingSecrets.copy(
      clientKey = BillingSecrets.clientKey.replace("MIGHAgEAMBMGByqGSM49", "MIGHAgEAMBMGByqGSM00")
    )
    val store: KafkaCredentialStore = credentialStore(corrupted)

    val producerStartup: Either[Throwable, Unit] =
      KafkaProducer.resource(producerSettings(store)).use(_ => IO.unit).attempt.unsafeRunSync()
    val adminStartup: Either[Throwable, Unit] =
      KafkaAdminClient.resource[IO](adminSettings(store)).use(_ => IO.unit).attempt.unsafeRunSync()

    for (startup <- List(producerStartup, adminStartup)) {
      val failure: Throwable = startup.swap.toOption.get
      assertTrue(failure.isInstanceOf[KafkaException], s"unexpected failure $failure")
      assertTrue(causes(failure).exists(_.getMessage.contains("PEM")),
        s"the cause names the PEM key store: ${causes(failure).map(_.getMessage)}")
    }
  }
}

object BillingClusterTlsCredentialsTest {

  /** Nothing listens here: starting a client loads its stores without contacting a broker. */
  val BootstrapServers: String = "localhost:19093"

  /** The PEM secrets the platform team issues to each service for the billing cluster. */
  final case class TlsSecrets(caCertificate: String, clientKey: String, clientCertificateChain: String)

  val BillingSecrets: TlsSecrets = TlsSecrets(
    caCertificate = """-----BEGIN CERTIFICATE-----
      |MIIBkjCCATegAwIBAgIUdoulotjcabBizsIDbwUd8FXfhHUwCgYIKoZIzj0EAwIw
      |HTEbMBkGA1UEAwwSQmlsbGluZyBDbHVzdGVyIENBMCAXDTI2MTAxOTEzMTQxNloY
      |DzIxMjYwOTI1MTMxNDE2WjAdMRswGQYDVQQDDBJCaWxsaW5nIENsdXN0ZXIgQ0Ew
      |WTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASjJ3MOgGHPSBXmIQVub63xiSM1+1PE
      |JNt7eqnO1OG0MNxl7LVousY6aGrK+xF8+S8ASYeYthjPjw0yATqZ+rr9o1MwUTAd
      |BgNVHQ4EFgQUoCq8t5LCFTnDDlIawtH6Pp8MgRkwHwYDVR0jBBgwFoAUoCq8t5LC
      |FTnDDlIawtH6Pp8MgRkwDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNJADBG
      |AiEAkroFduoPg+XBL/0wa66i2Y5DsQAY4iVy/bBAUPnYZpgCIQD8bTJwuB/J/wbT
      |Y37lfttfYcL2IRHor1qSPGuijJ4P3g==
      |-----END CERTIFICATE-----
      |""".stripMargin,
    clientKey = """-----BEGIN PRIVATE KEY-----
      |MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgP4lIvvZfaMIsNY8K
      |sKkAJrfZBxX7g7iDn0g1k1ssqTOhRANCAASZrbCBFurS7KL8I85wMHz1vqVdBXND
      |/C6xiPRFg6zH8FON5U0ac4bYcfPu+VnbbXaAm+fRT2n1xYABPUU+R1uf
      |-----END PRIVATE KEY-----
      |""".stripMargin,
    clientCertificateChain = """-----BEGIN CERTIFICATE-----
      |MIIBfjCCASOgAwIBAgIUC3Mb7N9LsKuy//ZtmEHuj9+iplgwCgYIKoZIzj0EAwIw
      |HTEbMBkGA1UEAwwSQmlsbGluZyBDbHVzdGVyIENBMCAXDTI2MTAxOTEzMTQyMloY
      |DzIxMjYwOTI1MTMxNDIyWjAaMRgwFgYDVQQDDA9pbnZvaWNlLXNlcnZpY2UwWTAT
      |BgcqhkjOPQIBBggqhkjOPQMBBwNCAASZrbCBFurS7KL8I85wMHz1vqVdBXND/C6x
      |iPRFg6zH8FON5U0ac4bYcfPu+VnbbXaAm+fRT2n1xYABPUU+R1ufo0IwQDAdBgNV
      |HQ4EFgQU/wnksiAyNMS+GO0bnyij++5d3BIwHwYDVR0jBBgwFoAUoCq8t5LCFTnD
      |DlIawtH6Pp8MgRkwCgYIKoZIzj0EAwIDSQAwRgIhAN3kLTl/71pYvmxlwOvPhxs4
      |pSWeEvlBYsbxfg5DMO1pAiEAwtVMLUkGK1vk1U1cagYI51VRDm2ENp1hm6X6WFUY
      |LEo=
      |-----END CERTIFICATE-----
      |""".stripMargin
  )

  def credentialStore(secrets: TlsSecrets): KafkaCredentialStore =
    KafkaCredentialStore.fromPemStrings(secrets.caCertificate, secrets.clientKey, secrets.clientCertificateChain)

  def producerSettings(store: KafkaCredentialStore): ProducerSettings[IO, String, String] =
    ProducerSettings[IO, String, String]
      .withBootstrapServers(BootstrapServers)
      .withClientId("invoice-publisher")
      .withCredentials(store)
      .withProperty(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
      .withCloseTimeout(5.seconds)

  def consumerSettings(store: KafkaCredentialStore): ConsumerSettings[IO, String, String] =
    ConsumerSettings[IO, String, String]
      .withBootstrapServers(BootstrapServers)
      .withClientId("invoice-reader")
      .withGroupId("billing-invoices")
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withCredentials(store)
      .withCloseTimeout(5.seconds)

  def adminSettings(store: KafkaCredentialStore): AdminClientSettings =
    AdminClientSettings(BootstrapServers)
      .withClientId("invoice-admin")
      .withCredentials(store)
      .withCloseTimeout(5.seconds)

  def singleLine(pem: String): String = pem.replace('\n', ' ')

  def clientIds(metrics: Map[MetricName, Metric]): Set[String] =
    metrics.keySet.flatMap(name => Option(name.tags.get("client-id")))

  def causes(failure: Throwable): List[Throwable] =
    LazyList.iterate(failure)(_.getCause).takeWhile(_ != null).toList
}
