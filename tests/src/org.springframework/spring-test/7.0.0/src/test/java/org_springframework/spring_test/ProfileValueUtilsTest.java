/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.junit.jupiter.api.Test;

import org.springframework.test.annotation.ProfileValueSource;
import org.springframework.test.annotation.ProfileValueSourceConfiguration;
import org.springframework.test.annotation.ProfileValueUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ProfileValueUtilsTest {
    @Test
    void retrievesConfiguredProfileValueSource() {
        ProfileValueSource profileValueSource =
                ProfileValueUtils.retrieveProfileValueSource(ProfileConfiguredTestCase.class);

        assertThat(profileValueSource).isInstanceOf(TestProfileValueSource.class);
        assertThat(profileValueSource.get("spring.profile")).isEqualTo("configured");
    }

    @ProfileValueSourceConfiguration(TestProfileValueSource.class)
    static class ProfileConfiguredTestCase {
    }

    public static class TestProfileValueSource implements ProfileValueSource {
        @Override
        public String get(String key) {
            if ("spring.profile".equals(key)) {
                return "configured";
            }
            return null;
        }
    }
}
