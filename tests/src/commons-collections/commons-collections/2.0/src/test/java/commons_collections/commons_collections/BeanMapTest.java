/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_collections.commons_collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import org.apache.commons.collections.BeanMap;
import org.junit.jupiter.api.Test;

public class BeanMapTest {
    @Test
    void getInvokesBeanGetter() {
        MutableBean bean = new MutableBean();
        bean.setName("initial");
        BeanMap beanMap = new BeanMap(bean);

        assertThat(beanMap.get("name")).isEqualTo("initial");
    }

    @Test
    void putInvokesBeanSetter() {
        MutableBean bean = new MutableBean();
        bean.setName("initial");
        BeanMap beanMap = new BeanMap(bean);

        assertThat(beanMap.put("name", "updated")).isEqualTo("initial");
        assertThat(bean.getName()).isEqualTo("updated");
        assertThat(beanMap.get("name")).isEqualTo("updated");
    }

    @Test
    void putConvertsValueWithSingleArgumentConstructor() {
        MutableBean bean = new MutableBean();
        BeanMap beanMap = new BeanMap(bean);

        assertThat(beanMap.put("constructed", "converted")).isNull();

        ConstructedValue converted = bean.getConstructed();
        assertThat(converted).isEqualTo(new ConstructedValue("converted"));
        assertThat(beanMap.get("constructed")).isEqualTo(converted);
    }

    @Test
    void convertTypeUsesPublicConstructorWhenAvailable() throws Exception {
        InspectableBeanMap beanMap = new InspectableBeanMap(new MutableBean());

        Object converted = beanMap.convertToConstructedValue("direct");

        assertThat(converted).isEqualTo(new ConstructedValue("direct"));
    }

    @Test
    void cloneInstantiatesANewBeanAndCopiesWritableProperties() throws Exception {
        MutableBean bean = new MutableBean();
        bean.setName("original");
        bean.setConstructed(new ConstructedValue("value"));
        BeanMap beanMap = new BeanMap(bean);

        BeanMap clonedMap = (BeanMap) beanMap.clone();

        MutableBean clonedBean = (MutableBean) clonedMap.getBean();
        assertThat(clonedBean).isNotSameAs(bean);
        assertThat(clonedMap.get("name")).isEqualTo("original");
        assertThat(clonedMap.get("constructed")).isEqualTo(new ConstructedValue("value"));
    }

    @Test
    void clearInstantiatesANewDefaultBean() {
        MutableBean bean = new MutableBean();
        bean.setName("changed");
        bean.setConstructed(new ConstructedValue("value"));
        BeanMap beanMap = new BeanMap(bean);

        beanMap.clear();

        MutableBean clearedBean = (MutableBean) beanMap.getBean();
        assertThat(clearedBean).isNotSameAs(bean);
        assertThat(beanMap.get("name")).isEqualTo("unset");
        assertThat(beanMap.get("constructed")).isNull();
    }

    public static class InspectableBeanMap extends BeanMap {
        public InspectableBeanMap(Object bean) {
            super(bean);
        }

        public Object convertToConstructedValue(Object value)
                throws InstantiationException, IllegalAccessException, InvocationTargetException {
            return convertType(ConstructedValue.class, value);
        }
    }

    public static class MutableBean {
        private String name = "unset";
        private ConstructedValue constructed;

        public MutableBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public ConstructedValue getConstructed() {
            return constructed;
        }

        public void setConstructed(ConstructedValue constructed) {
            this.constructed = constructed;
        }
    }

    public static class ConstructedValue {
        private final String value;

        public ConstructedValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof ConstructedValue)) {
                return false;
            }
            ConstructedValue that = (ConstructedValue) object;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
