/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.ValueIterator;
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyIntrospector;
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyReader;
import com.fasterxml.jackson.jr.ob.impl.JSONReader;
import com.fasterxml.jackson.jr.ob.impl.POJODefinition.Prop;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyReaderDynamicAccessTest {
    private static final JSON JSON_WITH_FORCE_ACCESS = JSON.std.with(JSON.Feature.FORCE_REFLECTION_ACCESS);

    @Test
    void assignsValuesWithDiscoveredFieldBackedPropertyReader() throws Exception {
        BeanPropertyReader propertyReader = discoveredReaderFor(FieldBackedReaderBean.class, "x");
        FieldBackedReaderBean bean = new FieldBackedReaderBean();

        propertyReader.setValueFor(bean, new Object[] {13});

        assertThat(bean.isConstructed()).isTrue();
        assertThat(bean.x).isEqualTo(13);
    }

    @Test
    void assignsValuesWithDiscoveredSetterBackedPropertyReader() throws Exception {
        BeanPropertyReader propertyReader = discoveredReaderFor(PublicSetterBackedReaderBean.class, "name");
        PublicSetterBackedReaderBean bean = new PublicSetterBackedReaderBean();

        propertyReader.setValueFor(bean, new Object[] {"Katherine"});

        assertThat(bean.getName()).isEqualTo("Katherine");
        assertThat(bean.getSetterCalls()).isEqualTo(1);
    }

    @Test
    void populatesFieldBackedBeanProperties() throws Exception {
        FieldBackedReaderBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(FieldBackedReaderBean.class, "{\"x\":3,\"y\":4}");

        assertThat(bean.isConstructed()).isTrue();
        assertThat(bean.x).isEqualTo(3);
        assertThat(bean.y).isEqualTo(4);
    }

    @Test
    void populatesPublicSetterBackedProperties() throws Exception {
        PublicSetterBackedReaderBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(PublicSetterBackedReaderBean.class,
                "{\"name\":\"Ada\"}");

        assertThat(bean.getName()).isEqualTo("Ada");
        assertThat(bean.getSetterCalls()).isEqualTo(1);
    }

    @Test
    void populatesFluentSetterBackedProperties() throws Exception {
        FluentSetterBackedReaderBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(FluentSetterBackedReaderBean.class,
                "{\"name\":\"Ada\"}");

        assertThat(bean.getName()).isEqualTo("Ada");
        assertThat(bean.getSetterCalls()).isEqualTo(1);
    }

    @Test
    void populatesPrivateSetterBackedProperties() throws Exception {
        SetterBackedReaderBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(SetterBackedReaderBean.class,
                "{\"name\":\"Ada\"}");

        assertThat(bean.getName()).isEqualTo("Ada");
        assertThat(bean.getSetterCalls()).isEqualTo(1);
    }

    @Test
    void populatesFieldBackedPropertiesFromBeanSequence() throws Exception {
        try (ValueIterator<FieldBackedReaderBean> iterator = JSON_WITH_FORCE_ACCESS
                .beanSequenceFrom(FieldBackedReaderBean.class, "[{\"x\":11,\"y\":12}]")) {
            assertThat(iterator.hasNext()).isTrue();

            FieldBackedReaderBean bean = iterator.next();

            assertThat(bean.isConstructed()).isTrue();
            assertThat(bean.x).isEqualTo(11);
            assertThat(bean.y).isEqualTo(12);
            assertThat(iterator.hasNext()).isFalse();
        }
    }

    @Test
    void populatesSetterBackedPropertiesFromBeanSequence() throws Exception {
        try (ValueIterator<PublicSetterBackedReaderBean> iterator = JSON_WITH_FORCE_ACCESS
                .beanSequenceFrom(PublicSetterBackedReaderBean.class, "[{\"name\":\"Grace\"}]")) {
            assertThat(iterator.hasNext()).isTrue();

            PublicSetterBackedReaderBean bean = iterator.next();

            assertThat(bean.getName()).isEqualTo("Grace");
            assertThat(bean.getSetterCalls()).isEqualTo(1);
            assertThat(iterator.hasNext()).isFalse();
        }
    }

    private static BeanPropertyReader discoveredReaderFor(Class<?> beanType, String propertyName) {
        JSONReader introspectionReader = JSON.builder().jsonReader();
        for (Prop property : BeanPropertyIntrospector.instance()
                .pojoDefinitionForDeserialization(introspectionReader, beanType).getProperties()) {
            if (propertyName.equals(property.name)) {
                return new BeanPropertyReader(property.name, property.field, property.setter);
            }
        }
        throw new IllegalArgumentException("No property named " + propertyName + " on " + beanType.getName());
    }

    public static final class FieldBackedReaderBean {
        private boolean constructed;
        public int x;
        public int y;

        public FieldBackedReaderBean() {
            constructed = true;
        }

        public boolean isConstructed() {
            return constructed;
        }
    }

    public static final class PublicSetterBackedReaderBean {
        private String name;
        private int setterCalls;

        public PublicSetterBackedReaderBean() {
        }

        public String getName() {
            return name;
        }

        public int getSetterCalls() {
            return setterCalls;
        }

        public void setName(String name) {
            this.name = name;
            setterCalls++;
        }
    }

    public static final class FluentSetterBackedReaderBean {
        private String name;
        private int setterCalls;

        public FluentSetterBackedReaderBean() {
        }

        public String getName() {
            return name;
        }

        public int getSetterCalls() {
            return setterCalls;
        }

        public FluentSetterBackedReaderBean setName(String name) {
            this.name = name;
            setterCalls++;
            return this;
        }
    }

    public static final class SetterBackedReaderBean {
        private String name;
        private int setterCalls;

        public SetterBackedReaderBean() {
        }

        public String getName() {
            return name;
        }

        public int getSetterCalls() {
            return setterCalls;
        }

        private void setName(String name) {
            this.name = name;
            setterCalls++;
        }
    }
}
