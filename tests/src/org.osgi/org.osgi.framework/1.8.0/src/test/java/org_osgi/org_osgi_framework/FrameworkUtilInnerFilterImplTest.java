/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_framework;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleException;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;

public class FrameworkUtilInnerFilterImplTest {
    @Test
    void matchesComparableValueUsingStaticValueOfFactory() throws Exception {
        final Filter filter = FrameworkUtil.createFilter("(version>=1.2.3)");
        final Map<String, ?> properties = Collections.singletonMap("version", Version.valueOf("1.2.4"));

        assertThat(filter.matches(properties)).isTrue();
    }

    @Test
    void attemptsToConvertUnknownValueUsingStringConstructor() throws Exception {
        final Filter filter = FrameworkUtil.createFilter("(failure=expected)");
        final Map<String, ?> properties = Collections.singletonMap("failure", new BundleException("expected"));

        assertThat(filter.matches(properties)).isFalse();
    }
}
