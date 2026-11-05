/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.lang.reflect.Constructor;
import java.security.AccessController;

import org.apache.aries.blueprint.utils.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("removal")
public class ReflectionUtilsAnonymous3Test {
    @Test
    void passesConstructorArgumentsWithAnAccessControlContext() throws Exception {
        Constructor<NamedSettings> constructor = NamedSettings.class.getConstructor(String.class);
        NamedSettings value = (NamedSettings) ReflectionUtils.newInstance(
                AccessController.getContext(), constructor, "remote");
        assertThat(value.region).isEqualTo("remote");
    }

    public static class NamedSettings {
        private final String region;

        public NamedSettings(String region) {
            this.region = region;
        }
    }
}
