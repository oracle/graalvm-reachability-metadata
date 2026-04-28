/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;

public class FrameworkUtilInnerFilterImplTest {
    @Test
    void matchesComparableValuesUsingStaticValueOfConversion() throws InvalidSyntaxException {
        Filter filter = FrameworkUtil.createFilter("(published>=2024-01-02)");

        assertThat(filter.matches(Map.of("published", Date.valueOf("2024-01-02")))).isTrue();
        assertThat(filter.matches(Map.of("published", Date.valueOf("2024-01-01")))).isFalse();
    }

    @Test
    void matchesComparableValuesUsingStringConstructorConversion() throws InvalidSyntaxException {
        Filter filter = FrameworkUtil.createFilter("(size>=42)");

        assertThat(filter.matches(Map.of("size", new BigInteger("42")))).isTrue();
        assertThat(filter.matches(Map.of("size", new BigInteger("41")))).isFalse();
    }
}
