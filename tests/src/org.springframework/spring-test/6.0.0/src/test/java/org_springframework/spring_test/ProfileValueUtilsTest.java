/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.junit.jupiter.api.Test;

import org.springframework.test.annotation.IfProfileValue;
import org.springframework.test.annotation.ProfileValueSource;
import org.springframework.test.annotation.ProfileValueSourceConfiguration;
import org.springframework.test.annotation.ProfileValueUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ProfileValueUtilsTest {
    @Test
    void usesConfiguredProfileValueSource() {
        CustomProfileValueSource.createdInstances = 0;

        boolean enabled = ProfileValueUtils.isTestEnabledInThisEnvironment(ProfileConfiguredTestCase.class);

        assertThat(enabled).isTrue();
        assertThat(CustomProfileValueSource.createdInstances).isEqualTo(1);
    }

    @IfProfileValue(name = "spring.test.profile", value = "enabled")
    @ProfileValueSourceConfiguration(CustomProfileValueSource.class)
    public static class ProfileConfiguredTestCase {
    }

    public static class CustomProfileValueSource implements ProfileValueSource {
        static int createdInstances;

        public CustomProfileValueSource() {
            createdInstances++;
        }

        @Override
        public String get(String key) {
            if ("spring.test.profile".equals(key)) {
                return "enabled";
            }
            return null;
        }
    }
}
