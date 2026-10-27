/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_common.wildfly_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.wildfly.common.array.Arrays2;

public class Arrays2Test {
    @Test
    void createsTypedArrayWithRequestedSize() {
        String[] values = Arrays2.createArray(String.class, 2);
        values[0] = "WildFly";
        values[1] = "Common";

        assertThat(values).isInstanceOf(String[].class).containsExactly("WildFly", "Common");
    }
}
