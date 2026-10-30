/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.simplefan;

import oracle.simplefan.FanManager;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class SimplefanTest {
    @Test
    void rejectsUnrecognizedDaemonConfiguration() {
        FanManager manager = FanManager.getInstance();
        Properties configuration = new Properties();
        configuration.setProperty("unsupportedOption", "value");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> manager.configure(configuration))
                .withMessageContaining("unrecognized property")
                .withMessageContaining("unsupportedOption");
    }

    @Test
    void requiresServiceNameForSubscription() {
        FanManager manager = FanManager.getInstance();
        Properties subscription = new Properties();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> manager.subscribe(subscription))
                .withMessageContaining("serviceName");
    }

    @Test
    void rejectsEmptyServiceName() {
        FanManager manager = FanManager.getInstance();
        Properties subscription = new Properties();
        subscription.setProperty("serviceName", "");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> manager.subscribe(subscription))
                .withMessageContaining("serviceName");
    }

    @Test
    void rejectsUnrecognizedSubscriptionProperties() {
        FanManager manager = FanManager.getInstance();
        Properties subscription = new Properties();
        subscription.setProperty("serviceName", "inventory.example");
        subscription.setProperty("unsupportedOption", "value");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> manager.subscribe(subscription))
                .withMessageContaining("unrecognized property");
    }
}
