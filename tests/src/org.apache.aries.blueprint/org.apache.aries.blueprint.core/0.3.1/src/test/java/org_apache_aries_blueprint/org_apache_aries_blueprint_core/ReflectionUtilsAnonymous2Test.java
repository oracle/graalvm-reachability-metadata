/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.security.AccessController;

import org.apache.aries.blueprint.utils.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("removal")
public class ReflectionUtilsAnonymous2Test {
    @Test
    void constructsBeanWithAnAccessControlContext() throws Exception {
        DefaultSettings value = (DefaultSettings) ReflectionUtils.newInstance(
                AccessController.getContext(), DefaultSettings.class);
        assertThat(value.region).isEqualTo("central");
    }

    public static class DefaultSettings {
        private final String region;

        public DefaultSettings() {
            region = "central";
        }
    }
}
