/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_core;

import org.glassfish.grizzly.utils.ArraySet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ArraySetTest {
    @Test
    void addsUniqueElementsAndReturnsAnIndependentTypedCopy() {
        ArraySet<String> names = new ArraySet<>(String.class);
        String[] empty = names.obtainArrayCopy();
        assertThat(empty).isEmpty();

        assertThat(names.addAll("oak", "pine", "oak")).isTrue();
        assertThat(names.size()).isEqualTo(2);
        String[] snapshot = names.obtainArrayCopy();
        assertThat(snapshot).containsExactly("oak", "pine");
        snapshot[0] = "changed";
        assertThat(names.getArray()).containsExactly("oak", "pine");

        assertThat(names.addAll(List.of("pine", "elm"))).isTrue();
        assertThat(names.getArray()).containsExactly("oak", "pine", "elm");
    }

    @Test
    void retainsOnlyRequestedElements() {
        ArraySet<String> names = new ArraySet<>(String.class);
        names.addAll("oak", "pine", "elm");

        assertThat(names.retainAll(List.of("pine", "elm", "ash"))).isTrue();
        assertThat(names.getArray()).containsExactly("pine", "elm");
        assertThat(names.retainAll(List.of("pine", "elm"))).isFalse();
        assertThat(names.retainAll(List.of())).isTrue();
        assertThat(names.obtainArrayCopy()).isEmpty();
    }

    @Test
    void removesCollectionMembersWithoutChangingTheSnapshot() {
        ArraySet<String> names = new ArraySet<>(String.class);
        names.addAll("oak", "pine", "elm");
        String[] snapshot = names.obtainArrayCopy();

        assertThat(names.removeAll(List.of("pine", "absent"))).isTrue();
        assertThat(names.getArray()).containsExactly("oak", "elm");
        assertThat(snapshot).containsExactly("oak", "pine", "elm");
        assertThat(names.removeAll(List.of("absent"))).isFalse();
    }
}
