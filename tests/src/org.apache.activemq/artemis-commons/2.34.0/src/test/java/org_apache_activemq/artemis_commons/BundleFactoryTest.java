/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.artemis.logs.ActiveMQUtilBundle;
import org.apache.activemq.artemis.logs.BundleFactory;
import org.junit.jupiter.api.Test;

public class BundleFactoryTest {
    @Test
    public void createsGeneratedBundleImplementation() {
        ActiveMQUtilBundle bundle = BundleFactory.newBundle(
                ActiveMQUtilBundle.class,
                "org.apache.activemq.artemis.tests.bundle-factory");

        assertThat(bundle).isNotNull();
        assertThat(bundle.getClass().getName()).isEqualTo(ActiveMQUtilBundle.class.getName() + "_impl");
        assertThat(bundle.failedToParseLong("not-a-number"))
                .hasMessage("AMQ209004: Failed to parse long value from not-a-number");
    }
}
