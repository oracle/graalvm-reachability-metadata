/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_actuator_autoconfigure;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.health.CompositeHealthContributorConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractCompositeHealthContributorConfigurationTest {

    @Test
    void createsIndicatorForSingleBean() {
        TestHealthContributorConfiguration configuration = new TestHealthContributorConfiguration();
        Map<String, TestClient> clients = Collections.singletonMap("primary", new TestClient("primary"));

        HealthContributor contributor = configuration.create(clients);

        assertThat(contributor).isInstanceOf(TestHealthIndicator.class);
        TestHealthIndicator indicator = (TestHealthIndicator) contributor;
        assertThat(indicator.health().getDetails()).containsEntry("client", "primary");
    }

    static final class TestHealthContributorConfiguration
            extends CompositeHealthContributorConfiguration<TestHealthIndicator, TestClient> {

        TestHealthContributorConfiguration() {
            super(TestHealthIndicator::new);
        }

        HealthContributor create(Map<String, TestClient> clients) {
            return createContributor(clients);
        }

    }

    public static final class TestHealthIndicator implements HealthIndicator {

        private final TestClient client;

        public TestHealthIndicator(TestClient client) {
            this.client = client;
        }

        @Override
        public Health health() {
            return Health.up().withDetail("client", this.client.name).build();
        }

    }

    public static final class TestClient {

        private final String name;

        TestClient(String name) {
            this.name = name;
        }

    }

}
