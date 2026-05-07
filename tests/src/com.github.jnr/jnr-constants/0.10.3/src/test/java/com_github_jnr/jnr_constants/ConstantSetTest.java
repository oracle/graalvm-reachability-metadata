/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_constants;

import jnr.constants.Constant;
import jnr.constants.ConstantSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstantSetTest {
    @Test
    void loadsPlatformConstantSetAndReadsBounds() {
        ConstantSet errnoSet = ConstantSet.getConstantSet("Errno");

        assertThat(errnoSet).isNotNull();
        Constant eperm = errnoSet.getConstant("EPERM");
        assertThat(eperm).isNotNull();
        assertThat(eperm.defined()).isTrue();
        assertThat(errnoSet.contains(eperm)).isTrue();
        assertThat(errnoSet.getConstant(eperm.longValue())).isEqualTo(eperm);
        assertThat(errnoSet.getValue("EPERM")).isEqualTo(eperm.longValue());
        assertThat(errnoSet.getName(eperm.intValue())).isEqualTo("EPERM");

        long minValue = errnoSet.minValue();
        long maxValue = errnoSet.maxValue();
        assertThat(minValue).isLessThanOrEqualTo(eperm.longValue());
        assertThat(maxValue).isGreaterThanOrEqualTo(eperm.longValue());
    }
}
