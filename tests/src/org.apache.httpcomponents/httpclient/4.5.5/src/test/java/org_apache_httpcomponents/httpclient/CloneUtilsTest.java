/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import org.apache.http.client.utils.CloneUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CloneUtilsTest {

    @Test
    void clonesObjectsThroughTheirPublicCloneMethod() throws Exception {
        CloneableValue original = new CloneableValue("value");

        CloneableValue copy = CloneUtils.cloneObject(original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getValue()).isEqualTo(original.getValue());
    }

    public static final class CloneableValue implements Cloneable {

        private final String value;

        public CloneableValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public CloneableValue clone() throws CloneNotSupportedException {
            return (CloneableValue) super.clone();
        }
    }
}
