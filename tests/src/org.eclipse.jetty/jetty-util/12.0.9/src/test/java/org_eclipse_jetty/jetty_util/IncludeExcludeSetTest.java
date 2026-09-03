/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.IncludeExcludeSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IncludeExcludeSetTest {
    @Test
    void appliesIncludedAndExcludedValuesFromReflectedBackingSets() {
        IncludeExcludeSet<String, String> values = new IncludeExcludeSet<>();
        values.include("allowed", "blocked");
        values.exclude("blocked");

        assertThat(values.test("allowed")).isTrue();
        assertThat(values.test("blocked")).isFalse();
        assertThat(values.test("other")).isFalse();
        assertThat(values.getIncluded()).containsExactlyInAnyOrder("allowed", "blocked");
        assertThat(values.getExcluded()).containsExactly("blocked");
    }
}
