/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class InnerClassPropertyTest {

    @Test
    void createsANonStaticInnerPropertyForItsContainingBean() throws Exception {
        final Namespace namespace = new ObjectMapper().readValue(
                "{\"policy\":{\"retentionHours\":72}}", Namespace.class);

        assertThat(namespace.getPolicy().getRetentionHours()).isEqualTo(72);
    }

    public static final class Namespace {
        private Policy policy;

        public Policy getPolicy() {
            return policy;
        }

        public void setPolicy(Policy policy) {
            this.policy = policy;
        }

        public final class Policy {
            private int retentionHours;

            public Policy() {
            }

            public int getRetentionHours() {
                return retentionHours;
            }

            public void setRetentionHours(int retentionHours) {
                this.retentionHours = retentionHours;
            }
        }
    }
}
