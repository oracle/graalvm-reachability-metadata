/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeResolverDynamicAccessTest {
    @Test
    void resolvesGenericArrayTypeArgumentsForInheritedBeanProperties() throws Exception {
        StringArrayContainer bean = JSON.std.beanFrom(StringArrayContainer.class,
                "{\"values\":[[\"alpha\"],[\"beta\",\"gamma\"]]}");
        List<?> values = bean.values;

        assertThat(values).hasSize(2);
        assertThat((Object[]) values.get(0)).containsExactly("alpha");
        assertThat((Object[]) values.get(1)).containsExactly("beta", "gamma");
    }

    public static class GenericArrayContainer<T> {
        public List<T[]> values;
    }

    public static class StringArrayContainer extends GenericArrayContainer<String> {
    }
}
