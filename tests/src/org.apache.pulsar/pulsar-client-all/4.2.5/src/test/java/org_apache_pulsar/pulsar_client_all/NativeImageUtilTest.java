/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.util.NativeImageUtil;
import org.junit.jupiter.api.Test;

public class NativeImageUtilTest {
    @Test
    void recognizesConfiguredReflectiveBean() {
        assertThat(NativeImageUtil.needsReflectionConfiguration(ConfiguredBean.class)).isFalse();
    }

    public static class ConfiguredBean {
        public String value;

        public ConfiguredBean() {
        }

        public String getValue() {
            return value;
        }
    }
}
