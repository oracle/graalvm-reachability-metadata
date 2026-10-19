/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.gson.Gson;

public final class GsonUnsafeAllocationSupport {
    private GsonUnsafeAllocationSupport() {
    }

    public static void deserializeWithoutNoArgsConstructor() {
        ConstructorOnly value = new Gson().fromJson("{\"name\":\"pulsar\"}", ConstructorOnly.class);

        assertThat(value.name).isEqualTo("pulsar");
        assertThat(value.constructed).isFalse();
    }

    private static final class ConstructorOnly {
        private String name;
        private boolean constructed;

        private ConstructorOnly(String name) {
            this.name = name;
            constructed = true;
        }
    }
}
