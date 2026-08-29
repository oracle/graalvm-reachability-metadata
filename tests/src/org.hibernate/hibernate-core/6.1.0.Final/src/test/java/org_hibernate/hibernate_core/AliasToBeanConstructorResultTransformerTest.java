/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.transform.AliasToBeanConstructorResultTransformer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

public class AliasToBeanConstructorResultTransformerTest {

    @Test
    public void createsAResultUsingTheConfiguredConstructor() throws Exception {
        Constructor<ResultValue> constructor = ResultValue.class.getConstructor(
                String.class,
                int.class
        );
        AliasToBeanConstructorResultTransformer<ResultValue> transformer =
                new AliasToBeanConstructorResultTransformer<>(constructor);

        ResultValue value = transformer.transformTuple(
                new Object[]{"hibernate", 6},
                new String[]{"name", "major"}
        );

        assertThat(value.getName()).isEqualTo("hibernate");
        assertThat(value.getMajor()).isEqualTo(6);
    }

    public static class ResultValue {
        private final String name;
        private final int major;

        public ResultValue(String name, int major) {
            this.name = name;
            this.major = major;
        }

        public String getName() {
            return name;
        }

        public int getMajor() {
            return major;
        }
    }
}
