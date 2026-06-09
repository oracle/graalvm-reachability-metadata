/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.util.NativeImageUtil;

public class NativeImageUtilTest {
    static {
        System.setProperty("org.graalvm.nativeimage.imagecode", "runtime");
    }

    @Test
    void detectsClassesWithAvailableReflectionMembers() {
        assertThat(NativeImageUtil.isInNativeImageAndIsAtRuntime()).isTrue();
        assertThat(NativeImageUtil.needsReflectionConfiguration(ReflectionVisibleBean.class)).isFalse();
    }

    public static class ReflectionVisibleBean {
        public String value;

        public ReflectionVisibleBean() {
        }

        public String getValue() {
            return value;
        }
    }
}
