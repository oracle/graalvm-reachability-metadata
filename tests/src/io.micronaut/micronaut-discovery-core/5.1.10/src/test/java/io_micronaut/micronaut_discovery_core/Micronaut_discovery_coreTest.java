/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_discovery_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.discovery.DiscoveryConfiguration;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.StaticServiceInstanceList;
import io.micronaut.discovery.cloud.NetworkInterface;
import io.micronaut.discovery.cloud.digitalocean.DigitalOceanInstanceMetadata;
import io.micronaut.discovery.event.ServiceReadyEvent;
import io.micronaut.discovery.event.ServiceStoppedEvent;
import io.micronaut.discovery.registration.RegistrationConfiguration;
import io.micronaut.health.HealthStatus;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Micronaut_discovery_coreTest {
    @Test
    public void buildsServiceInstanceWithDiscoveryAttributes() {
        ServiceInstance instance = ServiceInstance.builder("orders", URI.create("https://orders.example"))
                .instanceId("orders-1")
                .zone("zone-a")
                .region("region-a")
                .group("payments")
                .status(HealthStatus.UP)
                .metadata(Map.of("version", "v1", "owner", "platform"))
                .build();

        assertThat(instance.getId()).isEqualTo("orders");
        assertThat(instance.getURI()).hasToString("https://orders.example");
        assertThat(instance.getHost()).isEqualTo("orders.example");
        assertThat(instance.getPort()).isEqualTo(-1);
        assertThat(instance.isSecure()).isTrue();
        assertThat(instance.getInstanceId()).contains("orders-1");
        assertThat(instance.getZone()).contains("zone-a");
        assertThat(instance.getRegion()).contains("region-a");
        assertThat(instance.getGroup()).contains("payments");
        assertThat(instance.getHealthStatus()).isEqualTo(HealthStatus.UP);
        assertThat(instance.getMetadata().get("version", String.class)).contains("v1");
        assertThat(instance.resolve(URI.create("/health"))).hasToString("https://orders.example/health");
    }

    @Test
    public void createsInstancesFromConvenienceFactoriesAndStaticList() {
        ServiceInstance fromString = ServiceInstance.of("catalog", "localhost", 8080);
        ServiceInstance fromUri = ServiceInstance.of("billing", URI.create("http://billing.example:9090"));
        StaticServiceInstanceList list = new StaticServiceInstanceList(
                "catalog", List.of(URI.create("http://localhost:8080"), URI.create("http://localhost:8081")), "/api");

        assertThat(fromString.getURI()).hasToString("http://localhost:8080");
        assertThat(fromUri.getPort()).isEqualTo(9090);
        assertThat(list.getID()).isEqualTo("catalog");
        assertThat(list.getInstances()).extracting(ServiceInstance::getURI)
                .containsExactly(URI.create("http://localhost:8080"), URI.create("http://localhost:8081"));
        assertThat(list.getLoadBalancedURIs()).containsExactly(
                URI.create("http://localhost:8080"), URI.create("http://localhost:8081"));
        assertThat(list.getContextPath()).contains("/api");
    }

    @Test
    public void publishesServiceLifecycleEventsForAnInstance() {
        ServiceInstance instance = ServiceInstance.of("orders", URI.create("http://orders.example"));
        ServiceReadyEvent ready = new ServiceReadyEvent(instance);
        ServiceStoppedEvent stopped = new ServiceStoppedEvent(instance);

        assertThat(ready.getSource()).isSameAs(instance);
        assertThat(stopped.getSource()).isSameAs(instance);
        assertThat(ready).isNotEqualTo(stopped);
    }

    @Test
    public void configuresDiscoveryAndRegistrationDefaultsAndOverrides() {
        DiscoveryConfiguration discovery = new DiscoveryConfiguration() {
        };
        RegistrationConfiguration registration = new RegistrationConfiguration() {
        };

        assertThat(discovery.isEnabled()).isTrue();
        discovery.setEnabled(false);
        assertThat(discovery.isEnabled()).isFalse();
        assertThat(registration.isEnabled()).isTrue();
        assertThat(registration.getRetryCount()).isEqualTo(RegistrationConfiguration.DEFAULT_RETRY_COUNT);
        registration.setIpAddr("192.0.2.10");
        registration.setHealthPath("/health");
        registration.setTimeout(java.time.Duration.ofSeconds(10));
        registration.setPreferIpAddress(true);
        registration.setEnabled(false);

        assertThat(registration.getIpAddr()).contains("192.0.2.10");
        assertThat(registration.getHealthPath()).contains("/health");
        assertThat(registration.getTimeout()).contains(java.time.Duration.ofSeconds(10));
        assertThat(registration.isPreferIpAddress()).isTrue();
        assertThat(registration.isEnabled()).isFalse();
    }

    @Test
    public void mapsCloudInstanceMetadataAndNetworkDetails() {
        NetworkInterface network = new NetworkInterface();

        DigitalOceanInstanceMetadata metadata = new DigitalOceanInstanceMetadata();
        metadata.setInstanceId("instance-20");
        metadata.setRegion("fra1");
        metadata.setInterfaces(List.of(network));
        metadata.setTags(Map.of("role", "worker"));
        metadata.setUserData("bootstrap");
        metadata.setVendorData("provider");
        metadata.setCached(true);

        assertThat(metadata.getInstanceId()).isEqualTo("instance-20");
        assertThat(metadata.getRegion()).isEqualTo("fra1");
        assertThat(metadata.getInterfaces()).containsExactly(network);
        assertThat(metadata.getTags()).containsEntry("role", "worker");
        assertThat(metadata.getUserData()).isEqualTo("bootstrap");
        assertThat(metadata.getVendorData()).isEqualTo("provider");
        assertThat(metadata.isCached()).isTrue();
        assertThat(network.getName()).isNull();
        assertThat(network.getIpv4()).isNull();
    }
}
