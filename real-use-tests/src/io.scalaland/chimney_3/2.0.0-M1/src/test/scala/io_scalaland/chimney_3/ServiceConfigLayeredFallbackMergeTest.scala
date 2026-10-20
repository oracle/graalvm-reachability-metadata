/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Merging several sources into one target with `withFallback`, `withFallbackFrom` and the
 * option and collection fallback merge strategies.
 *
 * A deployment tool resolves the effective configuration of a service from layers: what the
 * operator typed on the command line, the tenant's defaults and the platform-wide defaults.
 * Each layer only knows some of the settings, so the effective configuration is assembled by
 * transforming the most specific layer and letting Chimney fill the remaining fields from the
 * fallbacks, in the order they were stacked. Optional settings that are converted on the way
 * into the effective configuration are merged with `orElse`, and tag lists are concatenated,
 * when the corresponding merge strategy is enabled.
 */
class ServiceConfigLayeredFallbackMergeTest {

  final case class CliOverrides(service: String, replicas: Int)
  final case class TenantDefaults(service: String, replicas: Int, region: String, tier: String)
  final case class PlatformDefaults(region: String, tier: String, logLevel: String, maxMemoryMb: Int)

  final case class DeploymentPlan(
      service: String,
      replicas: Int,
      region: String,
      tier: String,
      logLevel: String,
      maxMemoryMb: Int
  )

  final case class EndpointRequest(host: Option[String], port: Option[Int], tags: List[String])
  final case class EndpointDefaults(host: Option[String], port: Option[Int], tags: List[String])
  final case class Hostname(value: String)
  final case class Endpoint(host: Option[Hostname], port: Option[Long], tags: Vector[String])

  final case class NetworkInput(proxy: Option[String])
  final case class NetworkDefaults(proxy: Option[String], timeoutMs: Int, retries: Int)
  final case class ServiceInput(name: String, network: NetworkInput)
  final case class Network(proxy: Option[Hostname], timeoutMs: Int, retries: Int)
  final case class ServiceConfig(name: String, network: Network)

  /** The effective configuration keeps hosts typed and ports widened. */
  given Transformer[String, Hostname] = Hostname(_)
  given Transformer[Int, Long] = _.toLong

  private val tenant: TenantDefaults = TenantDefaults("ignored-service", 2, "eu-west-1", "standard")
  private val platform: PlatformDefaults = PlatformDefaults("us-east-1", "basic", "INFO", 512)

  @Test
  def fieldsMissingFromTheSourceComeFromStackedFallbacksInOrder(): Unit = {
    val plan: DeploymentPlan = CliOverrides("billing-api", 5)
      .into[DeploymentPlan]
      .withFallback(tenant)
      .withFallback(platform)
      .transform

    assertEquals(DeploymentPlan("billing-api", 5, "eu-west-1", "standard", "INFO", 512), plan)
  }

  @Test
  def overridesStillWinOverFallbackValues(): Unit = {
    val plan: DeploymentPlan = CliOverrides("search-api", 1)
      .into[DeploymentPlan]
      .withFallback(tenant)
      .withFallback(platform)
      .withFieldConst(_.logLevel, "DEBUG")
      .withFieldComputed(_.maxMemoryMb, cli => cli.replicas * 2048)
      .transform

    assertEquals(DeploymentPlan("search-api", 1, "eu-west-1", "standard", "DEBUG", 2048), plan)
  }

  @Test
  def aDefinedTransformerReusesItsFallbackForEverySource(): Unit = {
    val withPlatformDefaults: Transformer[TenantDefaults, DeploymentPlan] = Transformer
      .define[TenantDefaults, DeploymentPlan]
      .withFallback(platform)
      .buildTransformer

    val plans: List[DeploymentPlan] = List(
      TenantDefaults("orders", 3, "ap-south-1", "premium"),
      TenantDefaults("audit", 1, "eu-central-1", "basic")
    ).map(withPlatformDefaults.transform)

    assertEquals(
      List(
        DeploymentPlan("orders", 3, "ap-south-1", "premium", "INFO", 512),
        DeploymentPlan("audit", 1, "eu-central-1", "basic", "INFO", 512)
      ),
      plans
    )
  }

  @Test
  def optionalSettingsAndTagListsAreMergedByTheEnabledStrategies(): Unit = {
    val request: EndpointRequest = EndpointRequest(Some("api.internal"), None, List("canary"))
    val defaults: EndpointDefaults = EndpointDefaults(Some("api.default"), Some(8443), List("managed"))

    val sourceFirst: Endpoint = request
      .into[Endpoint]
      .withFallback(defaults)
      .enableOptionFallbackMerge(SourceOrElseFallback)
      .enableCollectionFallbackMerge(SourceAppendFallback)
      .transform
    val fallbackFirst: Endpoint = request
      .into[Endpoint]
      .withFallback(defaults)
      .enableOptionFallbackMerge(FallbackOrElseSource)
      .enableCollectionFallbackMerge(FallbackAppendSource)
      .transform
    val withoutMerging: Endpoint = request
      .into[Endpoint]
      .withFallback(defaults)
      .transform

    assertEquals(Endpoint(Some(Hostname("api.internal")), Some(8443L), Vector("canary", "managed")), sourceFirst)
    assertEquals(Endpoint(Some(Hostname("api.default")), Some(8443L), Vector("managed", "canary")), fallbackFirst)
    assertEquals(Endpoint(Some(Hostname("api.internal")), None, Vector("canary")), withoutMerging)
  }

  @Test
  def aNestedSourceValueGetsItsOwnFallback(): Unit = {
    val networkDefaults: NetworkDefaults = NetworkDefaults(Some("proxy.corp:3128"), 2500, 3)

    val direct: ServiceConfig = ServiceInput("ledger", NetworkInput(Some("proxy.dmz:8080")))
      .into[ServiceConfig]
      .withFallbackFrom(_.network)(networkDefaults)
      .transform
    val proxied: ServiceConfig = ServiceInput("reports", NetworkInput(None))
      .into[ServiceConfig]
      .withFallbackFrom(_.network)(networkDefaults)
      .enableOptionFallbackMerge(SourceOrElseFallback)
      .transform

    assertEquals(ServiceConfig("ledger", Network(Some(Hostname("proxy.dmz:8080")), 2500, 3)), direct)
    assertEquals(ServiceConfig("reports", Network(Some(Hostname("proxy.corp:3128")), 2500, 3)), proxied)
  }
}
