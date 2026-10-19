/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.javax.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.Test;

public class RuntimeDelegateFactoryFinderTest {
    private static final String PROVIDER =
            "org.apache.pulsar.shade.org.glassfish.jersey.internal.RuntimeDelegateImpl";

    @Test
    void instantiatesConfiguredDelegateWithDefiningClassLoader() {
        String key = RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY;
        String previous = System.getProperty(key);
        ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        System.setProperty(key, PROVIDER);
        Thread.currentThread().setContextClassLoader(null);
        RuntimeDelegate.setInstance(null);
        try {
            RuntimeDelegate delegate = RuntimeDelegate.getInstance();

            assertThat(delegate.createUriBuilder().path("topics").build().toString())
                    .isEqualTo("topics");
        } finally {
            RuntimeDelegate.setInstance(null);
            Thread.currentThread().setContextClassLoader(previousLoader);
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }
}
