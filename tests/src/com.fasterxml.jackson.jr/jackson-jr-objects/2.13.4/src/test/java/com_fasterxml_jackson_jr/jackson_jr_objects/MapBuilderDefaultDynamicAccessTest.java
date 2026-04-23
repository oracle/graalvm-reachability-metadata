/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class MapBuilderDefaultDynamicAccessTest {
    @Test
    void instantiatesConcreteMapImplementationsForBeanProperties() throws Exception {
        CustomMapBean bean = JSON.std.beanFrom(CustomMapBean.class, "{\"counts\":{\"beta\":2,\"alpha\":1}}");

        assertThat(bean.counts).isInstanceOf(CountsMap.class);
        assertThat(bean.counts).containsEntry("alpha", 1).containsEntry("beta", 2);
    }

    public static class CustomMapBean {
        public CountsMap counts;
    }

    public static class CountsMap extends LinkedHashMap<String, Integer> {
        public CountsMap() {
        }
    }
}
