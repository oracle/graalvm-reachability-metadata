/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;

public class FrameworkUtilFilterImpl1Test {
    @Test
    void matchesComparableValuesUsingStringConstructorConversion() throws InvalidSyntaxException {
        Filter filter = FrameworkUtil.createFilter("(version>=42)");

        assertThat(filter.matches(Map.of("version", new BigInteger("42")))).isTrue();
        assertThat(filter.matches(Map.of("version", new BigInteger("41")))).isFalse();
    }

    @Test
    void matchesUnknownValuesUsingStringConstructorConversion() throws InvalidSyntaxException {
        Filter filter = FrameworkUtil.createFilter("(locale=en)");

        assertThat(filter.matches(Map.of("locale", Locale.ENGLISH))).isTrue();
        assertThat(filter.matches(Map.of("locale", Locale.FRENCH))).isFalse();
    }
}
