/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.pulsar.shade.io.netty.util.Version;
import org.junit.jupiter.api.Test;

public class VersionTest {
    @Test
    void doesNotAdvertiseUnshadedNettyArtifacts() {
        Map<String, Version> versions = Version.identify();

        assertThat(versions).isEmpty();
    }
}
