/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.api.MapBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapBuilderDefaultDynamicAccessTest {
    private static final JSON JSON_WITH_CUSTOM_MAPS = JSON.std.with(MapBuilder.defaultImpl().newBuilder(CountsMap.class));

    @Test
    void instantiatesConfiguredMapImplementationForTopLevelMaps() throws Exception {
        Map<String, Object> counts = JSON_WITH_CUSTOM_MAPS.mapFrom("{\"alpha\":1,\"beta\":2}");

        assertThat(counts).isInstanceOf(CountsMap.class);
        assertThat(counts).containsEntry("alpha", 1).containsEntry("beta", 2);
    }

    @Test
    void instantiatesConfiguredMapImplementationForEmptyTopLevelMaps() throws Exception {
        Map<String, Object> counts = JSON_WITH_CUSTOM_MAPS.mapFrom("{}");

        assertThat(counts).isInstanceOf(CountsMap.class).isEmpty();
    }

    @Test
    void instantiatesConcreteMapImplementationsForBeanProperties() throws Exception {
        CustomMapBean bean = JSON.std.beanFrom(CustomMapBean.class, "{\"counts\":{\"alpha\":1,\"beta\":2}}");

        assertThat(bean.counts).isInstanceOf(CountsMap.class);
        assertThat(bean.counts).containsEntry("alpha", 1).containsEntry("beta", 2);
    }

    public static final class CustomMapBean {
        public CountsMap counts;
    }

    public static final class CountsMap extends LinkedHashMap<String, Object> {
        public CountsMap() {
        }
    }
}
