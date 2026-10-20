/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.http.client.utils.CloneUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CloneUtilsTest {
    @Test
    void invokesThePublicCloneContract() throws Exception {
        CloneableValue input = new CloneableValue("copy me");

        CloneableValue output = (CloneableValue) CloneUtils.clone(input);

        assertThat(output).isNotSameAs(input);
        assertThat(output.value).isEqualTo("copy me");
    }

    public static class CloneableValue implements Cloneable {
        public final String value;

        public CloneableValue(String value) {
            this.value = value;
        }

        @Override
        public CloneableValue clone() {
            return new CloneableValue(value);
        }
    }
}
