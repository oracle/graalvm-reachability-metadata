/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hyperledger_fabric_chaincode_java.fabric_chaincode_shim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.hyperledger.fabric.metrics.Metrics;
import org.hyperledger.fabric.metrics.MetricsProvider;
import org.junit.jupiter.api.Test;

public class MetricsTest {
    private static final String PROVIDER_CLASS = "org.hyperledger.fabric.metrics.impl.NullProvider";

    @Test
    void loadsConfiguredMetricsProvider() {
        Properties properties = new Properties();
        properties.setProperty("CHAINCODE_METRICS_ENABLED", "true");
        properties.setProperty("CHAINCODE_METRICS_PROVIDER", PROVIDER_CLASS);

        MetricsProvider provider = Metrics.initialize(properties);

        assertThat(provider.getClass().getName()).isEqualTo(PROVIDER_CLASS);
        assertThat(Metrics.getProvider()).isSameAs(provider);
    }
}
