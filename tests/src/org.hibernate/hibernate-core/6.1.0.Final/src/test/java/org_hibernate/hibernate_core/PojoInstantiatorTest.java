/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.tuple.PojoInstantiator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class PojoInstantiatorTest {

    @Test
    public void instantiatesAPojoThroughItsDefaultConstructor() {
        PojoInstantiator instantiator = new PojoInstantiator(SamplePojo.class, null);

        SamplePojo pojo = (SamplePojo) instantiator.instantiate();

        assertThat(pojo.getValue()).isEqualTo("constructed");
        assertThat(instantiator.isInstance(pojo)).isTrue();
    }

    public static class SamplePojo {
        private final String value;

        public SamplePojo() {
            value = "constructed";
        }

        public String getValue() {
            return value;
        }
    }
}
