/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.BeanMap;
import org.junit.jupiter.api.Test;

public class BeanMapTest {

    @Test
    public void readsAndWritesBeanPropertiesThroughMapAccess() {
        SampleBean bean = new SampleBean();
        bean.setName("alpha");
        BeanMap map = new BeanMap(bean);

        Object previousValue = map.put("name", "beta");

        assertThat(previousValue).isEqualTo("alpha");
        assertThat(map.get("name")).isEqualTo("beta");
        assertThat(bean.getName()).isEqualTo("beta");
    }

    @Test
    public void convertsStringValuesWithSingleArgumentConstructorBeforeWriting() {
        SampleBean bean = new SampleBean();
        bean.setWrapped(new WrappedValue("alpha"));
        BeanMap map = new BeanMap(bean);

        Object previousValue = map.put("wrapped", "beta");

        assertThat(previousValue).isEqualTo(new WrappedValue("alpha"));
        assertThat(bean.getWrapped()).isEqualTo(new WrappedValue("beta"));
        assertThat(map.get("wrapped")).isEqualTo(new WrappedValue("beta"));
    }

    @Test
    public void clearReplacesBeanWithDefaultInstance() {
        SampleBean bean = new SampleBean();
        bean.setName("custom");
        bean.setWrapped(new WrappedValue("custom"));
        BeanMap map = new BeanMap(bean);

        map.clear();

        assertThat(map.getBean()).isNotSameAs(bean);
        assertThat(map.get("name")).isEqualTo("default");
        assertThat(map.get("wrapped")).isEqualTo(new WrappedValue("default"));
    }

    @Test
    public void cloneCopiesReadableAndWritablePropertiesIntoNewBean() throws CloneNotSupportedException {
        SampleBean bean = new SampleBean();
        bean.setName("custom");
        bean.setWrapped(new WrappedValue("custom"));
        BeanMap original = new BeanMap(bean);

        BeanMap clone = (BeanMap) original.clone();

        assertThat(clone).isNotSameAs(original);
        assertThat(clone.getBean()).isNotSameAs(bean);
        assertThat(clone.get("name")).isEqualTo("custom");
        assertThat(clone.get("wrapped")).isEqualTo(new WrappedValue("custom"));
    }

    public static class SampleBean {
        private String name = "default";
        private WrappedValue wrapped = new WrappedValue("default");

        public SampleBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public WrappedValue getWrapped() {
            return wrapped;
        }

        public void setWrapped(WrappedValue wrapped) {
            this.wrapped = wrapped;
        }
    }

    public static class WrappedValue {
        private final String value;

        public WrappedValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof WrappedValue)) {
                return false;
            }
            WrappedValue that = (WrappedValue) object;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
