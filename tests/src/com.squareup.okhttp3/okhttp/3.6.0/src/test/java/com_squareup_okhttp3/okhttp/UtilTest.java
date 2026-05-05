/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.internal.Util;
import org.junit.jupiter.api.Test;

public class UtilTest {
    @Test
    void intersectReturnsTypedArrayOfCommonElements() {
        String[] result = Util.intersect(String.class,
                new String[] {"h2", "http/1.1", "spdy/3.1"},
                new String[] {"http/1.1", "h2"});

        assertThat(result).containsExactly("h2", "http/1.1");
        assertThat(result.getClass().getComponentType()).isEqualTo(String.class);
    }
}
