/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.ReflectionUtils;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {
    @Test
    void createsAnInstanceThroughItsMatchingDeclaredConstructor() {
        ConstructorFixture fixture = ReflectionUtils.newInstanceOf(ConstructorFixture.class, "created");

        assertThat(fixture.value).isEqualTo("created");
    }

    public static final class ConstructorFixture {
        private final String value;

        private ConstructorFixture(String value) {
            this.value = value;
        }
    }
}
