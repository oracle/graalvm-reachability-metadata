/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.transform.AliasToBeanResultTransformer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AliasToBeanResultTransformerTest {

    @Test
    public void createsAndPopulatesAResultBean() {
        AliasToBeanResultTransformer<ResultValue> transformer =
                new AliasToBeanResultTransformer<>(ResultValue.class);

        ResultValue value = transformer.transformTuple(
                new Object[]{"hibernate", 6},
                new String[]{"name", "major"}
        );

        assertThat(value.getName()).isEqualTo("hibernate");
        assertThat(value.getMajor()).isEqualTo(6);
    }

    public static class ResultValue {
        private String name;
        private int major;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getMajor() {
            return major;
        }

        public void setMajor(int major) {
            this.major = major;
        }
    }
}
