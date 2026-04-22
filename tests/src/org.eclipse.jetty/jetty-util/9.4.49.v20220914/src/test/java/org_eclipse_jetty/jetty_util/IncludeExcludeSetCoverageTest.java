/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.util.HashSet;

import org.eclipse.jetty.util.IncludeExcludeSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IncludeExcludeSetCoverageTest {
    @Test
    void includeExcludeSetBuildsBackingSetsReflectively() {
        IncludeExcludeSet<String, String> includeExcludeSet = new IncludeExcludeSet<>(HashSet.class);
        includeExcludeSet.include("include");
        includeExcludeSet.exclude("exclude");

        assertThat(includeExcludeSet.test("include")).isTrue();
        assertThat(includeExcludeSet.test("exclude")).isFalse();
    }
}
