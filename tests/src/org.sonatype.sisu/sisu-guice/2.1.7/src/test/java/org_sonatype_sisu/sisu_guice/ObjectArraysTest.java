/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.internal.util.ImmutableSet;
import org.junit.jupiter.api.Test;

public class ObjectArraysTest {
    @Test
    void immutableSetToArrayExpandsEmptyTypedArray() {
        ImmutableSet<String> names = ImmutableSet.of("sisu");

        CharSequence[] expanded = names.toArray(new CharSequence[0]);

        assertThat(expanded).isInstanceOf(CharSequence[].class);
        assertThat(expanded).containsExactly("sisu");
    }
}
