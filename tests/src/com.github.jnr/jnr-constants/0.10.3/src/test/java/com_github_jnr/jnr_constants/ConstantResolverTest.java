/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_constants;

import jnr.constants.platform.Errno;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstantResolverTest {
    @Test
    void resolvesKnownConstantBySmallNumericValue() {
        int epermValue = Errno.EPERM.intValue();

        assertThat(epermValue).isBetween(0, 255);
        assertThat(Errno.valueOf(epermValue)).isEqualTo(Errno.EPERM);
        assertThat(Errno.valueOf(epermValue)).isSameAs(Errno.EPERM);
    }
}
