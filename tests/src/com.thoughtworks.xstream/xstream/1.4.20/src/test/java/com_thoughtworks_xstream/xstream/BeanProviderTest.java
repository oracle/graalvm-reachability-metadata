/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import com.thoughtworks.xstream.converters.javabean.BeanProvider;
import com.thoughtworks.xstream.converters.javabean.JavaBeanProvider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanProviderTest {
    @Test
    void createsWritesAndVisitsJavaBeanProperties() {
        BeanProvider provider = new BeanProvider();

        MutableBean bean = (MutableBean)provider.newInstance(MutableBean.class);
        provider.writeProperty(bean, "name", "xstream");
        provider.writeProperty(bean, "count", 20);

        List<String> visitedProperties = new ArrayList<>();
        provider.visitSerializableProperties(bean, new JavaBeanProvider.Visitor() {
            @Override
            public boolean shouldVisit(String name, Class definedIn) {
                return MutableBean.class.equals(definedIn);
            }

            @Override
            public void visit(String propertyName, Class fieldType, Class definedIn, Object newObj) {
                visitedProperties.add(propertyName + ":" + fieldType.getName() + "=" + newObj);
            }
        });

        assertThat(bean.getName()).isEqualTo("xstream");
        assertThat(bean.getCount()).isEqualTo(20);
        assertThat(provider.propertyDefinedInClass("name", MutableBean.class)).isTrue();
        assertThat(provider.propertyWriteable("count", MutableBean.class)).isTrue();
        assertThat(provider.getPropertyType(bean, "name")).isEqualTo(String.class);
        assertThat(visitedProperties).containsExactlyInAnyOrder(
                "count:int=20",
                "name:java.lang.String=xstream");
    }

    @Test
    void findsPublicDefaultConstructor() {
        ExposedBeanProvider provider = new ExposedBeanProvider();

        Constructor defaultConstructor = provider.defaultConstructorFor(MutableBean.class);

        assertThat(defaultConstructor).isNotNull();
        assertThat(defaultConstructor.getParameterTypes()).isEmpty();
        assertThat(defaultConstructor.getDeclaringClass()).isEqualTo(MutableBean.class);
    }

    private static final class ExposedBeanProvider extends BeanProvider {
        Constructor defaultConstructorFor(Class type) {
            return getDefaultConstrutor(type);
        }
    }

    public static final class MutableBean {
        private String name;
        private int count;

        public MutableBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
