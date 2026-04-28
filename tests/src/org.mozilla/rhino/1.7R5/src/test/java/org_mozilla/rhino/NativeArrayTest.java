/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.NativeArray;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeArrayTest {
    @Test
    void expandsTypedDestinationArrayWhenConvertingToArray() {
        NativeArray array = new NativeArray(new Object[] {"first", "second", "third"});

        Object[] converted = array.toArray(new String[0]);

        assertThat(converted).isInstanceOf(String[].class);
        assertThat(converted).containsExactly("first", "second", "third");
    }
}
