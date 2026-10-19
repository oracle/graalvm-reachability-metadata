/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.apache.pulsar.shade.javax.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.Test;

public class RuntimeDelegateTest {
    @Test
    void discoversDelegateAndCreatesUriBuilder() {
        RuntimeDelegate delegate = RuntimeDelegate.getInstance();

        URI uri = delegate.createUriBuilder().path("topics").path("persistent").build();

        assertThat(delegate.getClass().getName()).contains("RuntimeDelegateImpl");
        assertThat(uri.toString()).isEqualTo("topics/persistent");
    }
}
