/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.objenesis.instantiator.util.ClassUtils;

/** Verifies Objenesis class lookup and default construction utilities. */
public class ObjenesisInstantiatorUtilClassUtilsTest {
    @Test
    void loadsExistingClassAndCreatesInstance() {
        Class<Fixture> type = ClassUtils.getExistingClass(
                getClass().getClassLoader(), Fixture.class.getName());

        Fixture instance = ClassUtils.newInstance(type);

        assertThat(instance.value).isEqualTo("constructed");
    }

    public static final class Fixture {
        private final String value;

        public Fixture() {
            value = "constructed";
        }
    }
}
