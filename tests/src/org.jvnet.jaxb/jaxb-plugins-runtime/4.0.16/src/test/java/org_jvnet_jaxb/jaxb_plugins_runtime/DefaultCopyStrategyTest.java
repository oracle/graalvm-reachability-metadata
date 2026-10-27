/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jvnet_jaxb.jaxb_plugins_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.jvnet.jaxb.lang.DefaultCopyStrategy;
import org.junit.jupiter.api.Test;

public class DefaultCopyStrategyTest {
    @Test
    void copiesCloneableValuesUsingTheirPublicCloneMethod() {
        CloneableValue original = new CloneableValue("value");

        Object copiedValue = DefaultCopyStrategy.getInstance().copy(null, original);

        assertThat(copiedValue).isInstanceOf(CloneableValue.class);
        CloneableValue copied = (CloneableValue) copiedValue;
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.value).isEqualTo(original.value);
    }

    public static final class CloneableValue implements Cloneable {
        private final String value;

        public CloneableValue(String value) {
            this.value = value;
        }

        @Override
        public CloneableValue clone() {
            try {
                return (CloneableValue) super.clone();
            } catch (CloneNotSupportedException exception) {
                throw new AssertionError(exception);
            }
        }
    }
}
