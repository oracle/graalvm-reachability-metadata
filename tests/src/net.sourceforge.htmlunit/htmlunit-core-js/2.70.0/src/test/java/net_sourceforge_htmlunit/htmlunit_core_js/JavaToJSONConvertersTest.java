/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.util.Map;

import net.sourceforge.htmlunit.corejs.javascript.JavaToJSONConverters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaToJSONConvertersTest {
    public static final class SerializableBean {
        private final String name;
        private final int count;

        public SerializableBean(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }
    }

    @Test
    void beanConverterInvokesReadableBeanAccessors() {
        Object converted = JavaToJSONConverters.BEAN.apply(new SerializableBean("htmlunit", 3));

        assertThat(converted).isInstanceOf(Map.class);
        Map<?, ?> bean = (Map<?, ?>) converted;
        assertThat(bean.get("beanClass")).isEqualTo(SerializableBean.class.getName());
        assertThat(bean.get("properties")).isInstanceOf(Map.class);
        Map<?, ?> properties = (Map<?, ?>) bean.get("properties");
        assertThat(properties.get("name")).isEqualTo("htmlunit");
        assertThat(properties.get("count")).isEqualTo(3);
    }
}
