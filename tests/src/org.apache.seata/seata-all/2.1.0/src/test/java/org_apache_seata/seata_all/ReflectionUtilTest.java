/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.seata.common.util.ReflectionUtil;
import org.junit.jupiter.api.Test;

public class ReflectionUtilTest {
    @Test
    void classLookupAndFieldScanningUseReflectionUtilities() throws Exception {
        Class<?> loadedClass = ReflectionUtil.getClassByName(ReflectiveFixture.class.getName());

        Field[] fields = ReflectionUtil.getAllFields(loadedClass);
        Set<String> fieldNames = Arrays.stream(fields)
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(loadedClass).isSameAs(ReflectiveFixture.class);
        assertThat(fieldNames).contains("baseValue", "message", "counter");
        assertThat(fieldNames).doesNotContain("staticValue");
    }

    @Test
    void fieldValuesCanBeReadAndUpdatedByName() throws Exception {
        ReflectiveFixture fixture = new ReflectiveFixture("initial", 3);

        assertThat((String) ReflectionUtil.getFieldValue(fixture, "message")).isEqualTo("initial");
        assertThat((Integer) ReflectionUtil.getFieldValue(fixture, "baseValue")).isEqualTo(3);

        ReflectionUtil.setFieldValue(fixture, "message", "updated");
        ReflectionUtil.setFieldValue(fixture, "baseValue", 9);

        assertThat(fixture.describe()).isEqualTo("updated:9:1");
    }

    @Test
    void staticFieldsCanBeUpdatedByName() throws Exception {
        String originalValue = ReflectiveFixture.staticValue;
        try {
            ReflectionUtil.modifyStaticFinalField(ReflectiveFixture.class, "staticValue", "changed");

            assertThat(ReflectiveFixture.staticValue).isEqualTo("changed");
        } finally {
            ReflectionUtil.modifyStaticFinalField(ReflectiveFixture.class, "staticValue", originalValue);
        }
    }

    @Test
    void methodsCanBeFoundAndInvokedByName() throws Exception {
        ReflectiveFixture fixture = new ReflectiveFixture("segment", 4);

        Object result = ReflectionUtil.invokeMethod(
                fixture,
                "format",
                new Class<?>[] {String.class, int.class},
                "prefix",
                2);

        assertThat(result).isEqualTo("prefix-segment:8");
    }

    public static class BaseFixture {
        protected int baseValue;

        protected BaseFixture(int baseValue) {
            this.baseValue = baseValue;
        }
    }

    public static class ReflectiveFixture extends BaseFixture {
        private static String staticValue = "original";

        private String message;
        private int counter = 1;

        public ReflectiveFixture(String message, int baseValue) {
            super(baseValue);
            this.message = message;
        }

        public String describe() {
            return message + ":" + baseValue + ":" + counter;
        }

        private String format(String prefix, int multiplier) {
            return prefix + "-" + message + ":" + (baseValue * multiplier);
        }
    }
}
