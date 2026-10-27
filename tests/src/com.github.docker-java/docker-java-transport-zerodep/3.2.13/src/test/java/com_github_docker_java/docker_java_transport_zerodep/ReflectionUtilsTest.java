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
    void callsBeanAccessorsByName() {
        AccessorTarget target = new AccessorTarget();

        ReflectionUtils.callSetter(target, "Endpoint", String.class, "unix:///var/run/docker.sock");

        assertThat(target.getEndpoint()).isEqualTo("unix:///var/run/docker.sock");
        assertThat(ReflectionUtils.callGetter(target, "Endpoint", String.class)).isEqualTo("unix:///var/run/docker.sock");
    }

    public static final class AccessorTarget {
        private String endpoint;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(final String endpoint) {
            this.endpoint = endpoint;
        }
    }
}
