/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.util.ObjectUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectUtilTest {

    @Test
    void convertLoadsClassFromStringName() {
        Class<?> convertedClass = ObjectUtil.convert("java.lang.String", Class.class);

        assertThat(convertedClass).isEqualTo(String.class);
    }

    @Test
    void getPropertyReadsValueThroughPublicGetter() {
        ReadableFixture fixture = new ReadableFixture("liquibase");

        Object property = ObjectUtil.getProperty(fixture, "name");

        assertThat(property).isEqualTo("liquibase");
    }

    public static class ReadableFixture {
        private final String name;

        public ReadableFixture(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
