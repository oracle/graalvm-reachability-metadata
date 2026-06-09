/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.util.Util;
import org.junit.jupiter.api.Test;

public class UtilTest {
    @Test
    void stringUsesObjectsToStringOverride() {
        assertThat(Util.string(new DescriptiveObject("kryo"))).isEqualTo("descriptive:kryo");
    }

    public static class DescriptiveObject {
        private final String value;

        DescriptiveObject(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "descriptive:" + value;
        }
    }
}
