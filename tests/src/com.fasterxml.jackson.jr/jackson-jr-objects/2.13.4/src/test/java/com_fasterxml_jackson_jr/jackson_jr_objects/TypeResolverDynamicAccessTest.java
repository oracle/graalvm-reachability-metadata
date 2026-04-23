/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeResolverDynamicAccessTest {
    @Test
    void resolvesInheritedGenericArraySetterTypes() throws Exception {
        StringArrayContainer bean = JSON.std.beanFrom(StringArrayContainer.class,
                "{\"values\":[\"alpha\",\"beta\"]}");
        Object[] values = ((GenericArrayContainer<?>) bean).getValues();

        assertThat(values).containsExactly("alpha", "beta");
    }

    static class GenericArrayContainer<T> {
        private T[] values;

        public T[] getValues() {
            return values;
        }

        public void setValues(T[] values) {
            this.values = values;
        }
    }

    static final class StringArrayContainer extends GenericArrayContainer<String> {
    }
}
