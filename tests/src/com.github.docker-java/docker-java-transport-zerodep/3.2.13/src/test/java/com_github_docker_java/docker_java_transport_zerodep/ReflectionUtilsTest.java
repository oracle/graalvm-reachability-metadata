/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_docker_java.docker_java_transport_zerodep;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    @Test
    void callsSetterAndGetterByName() {
        final MutableSettings settings = new MutableSettings();

        ReflectionUtils.callSetter(settings, "Endpoint", String.class, "unix:///var/run/docker.sock");
        final String endpoint = ReflectionUtils.callGetter(settings, "Endpoint", String.class);

        assertThat(endpoint).isEqualTo("unix:///var/run/docker.sock");
    }

    public static final class MutableSettings {
        private String endpoint;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(final String endpoint) {
            this.endpoint = endpoint;
        }
    }
}
