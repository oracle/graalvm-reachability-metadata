/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.Test;

import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

public class MapBuilderDefaultDynamicAccessTest {
    @Test
    void instantiatesConcreteMapImplementationsForBeanProperties() throws Exception {
        TreeMapBean bean = JSON.std.beanFrom(TreeMapBean.class, "{\"counts\":{\"beta\":2,\"alpha\":1}}");

        assertThat(bean.counts).isInstanceOf(TreeMap.class);
        assertThat(bean.counts).containsEntry("alpha", 1).containsEntry("beta", 2);
    }

    public static class TreeMapBean {
        public TreeMap<String, Integer> counts;
    }
}
