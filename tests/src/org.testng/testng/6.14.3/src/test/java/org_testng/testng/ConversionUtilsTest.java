/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.testng.ConversionUtils;

public class ConversionUtilsTest {
    @Test
    void wrapsJUnitParametersByConstructingTestInstances() {
        Collection<Object[]> parameters = Arrays.asList(
                new Object[] {"first", 1},
                new Object[] {"second", 2});

        Object[] instances = ConversionUtils.wrapDataProvider(ParameterBackedTestClass.class, parameters);

        assertThat(instances).hasSize(2);
        assertThat(instances)
                .allSatisfy(instance -> assertThat(instance).isInstanceOf(ParameterBackedTestClass.class))
                .extracting(instance -> ((ParameterBackedTestClass) instance).name)
                .containsExactly("first", "second");
        assertThat(instances)
                .extracting(instance -> ((ParameterBackedTestClass) instance).index)
                .containsExactly(1, 2);
    }

    public static final class ParameterBackedTestClass {
        private final String name;
        private final int index;

        public ParameterBackedTestClass(String name, int index) {
            this.name = name;
            this.index = index;
        }
    }
}
