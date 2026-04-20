/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml.snakeyaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

public class ConstructorConstructScalarTest {

    @Test
    void constructsCustomScalarWithSingleDeclaredConstructor() {
        SingleIntegerScalar value = new Yaml().loadAs("42", SingleIntegerScalar.class);

        assertThat(value.getValue()).isEqualTo(42);
    }

    @Test
    void constructsCustomScalarWithStringConstructorWhenMultipleDeclaredConstructorsExist() {
        MultiConstructorScalar value = new Yaml().loadAs("007", MultiConstructorScalar.class);

        assertThat(value.getConstructionPath()).isEqualTo("string:007");
    }

    @Test
    void constructsDateSubclassWithPublicLongConstructor() {
        String yaml = "2024-03-01T10:15:30Z";

        Date expected = new Yaml().loadAs(yaml, Date.class);
        TimestampWrapper actual = new Yaml().loadAs(yaml, TimestampWrapper.class);

        assertThat(actual).isInstanceOf(TimestampWrapper.class);
        assertThat(actual.getTime()).isEqualTo(expected.getTime());
    }

    public static final class SingleIntegerScalar {
        private final int value;

        private SingleIntegerScalar(Integer value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public static final class MultiConstructorScalar {
        private final String constructionPath;

        private MultiConstructorScalar(Integer value) {
            this.constructionPath = "integer:" + value;
        }

        private MultiConstructorScalar(String value) {
            this.constructionPath = "string:" + value;
        }

        public String getConstructionPath() {
            return constructionPath;
        }
    }

    public static final class TimestampWrapper extends Date {
        public TimestampWrapper(long time) {
            super(time);
        }
    }
}
