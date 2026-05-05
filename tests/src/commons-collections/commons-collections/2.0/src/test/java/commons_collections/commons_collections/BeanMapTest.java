/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_collections.commons_collections;

import org.apache.commons.collections.BeanMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanMapTest {
    @Test
    void putConvertsValueWithTargetTypeConstructorAndInvokesAccessorMethods() {
        BeanBackedSettings settings = new BeanBackedSettings();
        settings.setName("initial");
        settings.setCount(Integer.valueOf(7));

        BeanMap beanMap = new BeanMap(settings);

        assertThat(beanMap.get("name")).isEqualTo("initial");
        assertThat(beanMap.put("count", "42")).isEqualTo(Integer.valueOf(7));

        assertThat(settings.getCount()).isEqualTo(Integer.valueOf(42));
        assertThat(beanMap.get("count")).isEqualTo(Integer.valueOf(42));
    }

    @Test
    void clearReplacesWrappedBeanWithANewInstance() {
        BeanBackedSettings settings = new BeanBackedSettings();
        settings.setName("custom");
        settings.setCount(Integer.valueOf(99));
        BeanMap beanMap = new BeanMap(settings);

        beanMap.clear();

        assertThat(beanMap.getBean()).isInstanceOf(BeanBackedSettings.class).isNotSameAs(settings);
        assertThat(beanMap.get("name")).isEqualTo("default-name");
        assertThat(beanMap.get("count")).isEqualTo(Integer.valueOf(1));
    }

    @Test
    void cloneCreatesANewBeanAndCopiesWritableProperties() throws CloneNotSupportedException {
        BeanBackedSettings settings = new BeanBackedSettings();
        settings.setName("source");
        settings.setCount(Integer.valueOf(13));
        BeanMap beanMap = new BeanMap(settings);

        BeanMap clonedMap = (BeanMap) beanMap.clone();

        assertThat(clonedMap).isNotSameAs(beanMap);
        assertThat(clonedMap.getBean()).isInstanceOf(BeanBackedSettings.class).isNotSameAs(settings);
        assertThat(clonedMap.get("name")).isEqualTo("source");
        assertThat(clonedMap.get("count")).isEqualTo(Integer.valueOf(13));
    }

    public static final class BeanBackedSettings {
        private String name = "default-name";
        private Integer count = Integer.valueOf(1);

        public BeanBackedSettings() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }
}
