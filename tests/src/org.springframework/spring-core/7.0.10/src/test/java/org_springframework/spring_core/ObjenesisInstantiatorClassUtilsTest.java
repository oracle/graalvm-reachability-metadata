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
public class ObjenesisInstantiatorClassUtilsTest {
    @Test
    void loadsExistingClassAndCreatesInstance() {
        Class<Fixture> type = ClassUtils.getExistingClass(
                Fixture.class.getClassLoader(), Fixture.class.getName());

        Fixture fixture = ClassUtils.newInstance(type);

        assertThat(type).isEqualTo(Fixture.class);
        assertThat(fixture.value).isEqualTo("created");
    }

    public static final class Fixture {
        private final String value;

        public Fixture() {
            this.value = "created";
        }
    }
}
