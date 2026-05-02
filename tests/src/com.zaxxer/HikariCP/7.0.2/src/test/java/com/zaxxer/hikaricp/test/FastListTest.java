/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import com.zaxxer.hikari.util.FastList;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class FastListTest {
    @Test
    void supportsRandomAccessMutationAndArrayCopies() {
        FastList<String> values = new FastList<>(String.class, 1);

        values.add("first");
        values.add("second");
        values.addAll(Collections.singletonList("third"));
        values.set(1, "updated");

        assertThat(values).containsExactly("first", "updated", "third");
        assertThat(values.indexOf("updated")).isEqualTo(1);
        assertThat(values.toArray(new String[0])).containsExactly("first", "updated", "third");
        assertThat(values.remove("updated")).isTrue();
        assertThat(values.removeLast()).isEqualTo("third");
        assertThat(values.remove(0)).isEqualTo("first");
        assertThat(values).isEmpty();
    }
}
