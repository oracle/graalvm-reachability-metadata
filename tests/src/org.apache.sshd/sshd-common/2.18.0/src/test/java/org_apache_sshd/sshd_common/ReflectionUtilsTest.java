/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.sshd.common.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {
    public static class FieldFixture {
        public static final String PUBLIC_FIELD = "public";
        private final String declaredField = "declared";
    }

    @Test
    void findsPublicAndDeclaredFieldsAndLoadsClasses() {
        assertThat(ReflectionUtils.getMatchingFields(
                FieldFixture.class,
                field -> ReflectionUtils.FIELD_NAME_EXTRACTOR.apply(field).equals("PUBLIC_FIELD")))
                .extracting(ReflectionUtils.FIELD_NAME_EXTRACTOR)
                .containsExactly("PUBLIC_FIELD");
        assertThat(ReflectionUtils.getMatchingDeclaredFields(
                FieldFixture.class,
                field -> ReflectionUtils.FIELD_NAME_EXTRACTOR.apply(field).equals("declaredField")))
                .extracting(ReflectionUtils.FIELD_NAME_EXTRACTOR)
                .containsExactly("declaredField");
        assertThat(ReflectionUtils.isClassAvailable(
                ReflectionUtilsTest.class.getClassLoader(), String.class.getName())).isTrue();
        assertThat(ReflectionUtils.isClassAvailable(
                ReflectionUtilsTest.class.getClassLoader(), "missing.sshd.Class")).isFalse();
    }
}
