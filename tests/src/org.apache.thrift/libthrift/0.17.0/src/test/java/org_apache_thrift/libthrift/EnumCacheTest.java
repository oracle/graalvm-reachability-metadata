/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_thrift.libthrift;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.thrift.TEnum;
import org.apache.thrift.partial.EnumCache;
import org.junit.jupiter.api.Test;

public class EnumCacheTest {
    @Test
    void getBuildsCacheFromGeneratedEnumValuesMethod() {
        EnumCache cache = new EnumCache();

        TEnum enumValue = cache.get(ExampleCachedEnum.class, 2);

        assertThat(enumValue).isSameAs(ExampleCachedEnum.SECOND);
        assertThat(enumValue.getValue()).isEqualTo(2);
        assertThat(cache.get(ExampleCachedEnum.class, 99)).isNull();
    }

    public enum ExampleCachedEnum implements TEnum {
        FIRST(1),
        SECOND(2);

        private final int value;

        ExampleCachedEnum(int value) {
            this.value = value;
        }

        @Override
        public int getValue() {
            return value;
        }
    }
}
