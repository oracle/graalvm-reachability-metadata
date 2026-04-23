/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;

public class FrameworkUtil1Test {
    @Test
    void createFilterMatchesPlainStringProperties() throws InvalidSyntaxException {
        Filter filter = FrameworkUtil.createFilter("(name=osgi)");

        assertThat(filter.matches(Map.of("name", "osgi"))).isTrue();
        assertThat(filter.matches(Map.of("name", "other"))).isFalse();
    }
}
