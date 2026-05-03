/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.List;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.api.CollectionBuilder;
import com.fasterxml.jackson.jr.ob.api.MapBuilder;
import com.fasterxml.jackson.jr.ob.impl.BeanConstructors;
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyIntrospector;
import com.fasterxml.jackson.jr.ob.impl.JSONReader;
import com.fasterxml.jackson.jr.ob.impl.JSONWriter;
import com.fasterxml.jackson.jr.ob.impl.POJODefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyIntrospectorDynamicAccessTest {
    private static final BeanPropertyIntrospector INTROSPECTOR = BeanPropertyIntrospector.instance();
    private static final JSONReader JSON_READER = new JSONReader(CollectionBuilder.defaultImpl(), MapBuilder.defaultImpl());
    private static final JSONWriter JSON_WRITER = new JSONWriter();
    private static final JSON JSON_WITH_FORCE_ACCESS = JSON.std.with(JSON.Feature.FORCE_REFLECTION_ACCESS);
    private static final JSON JSON_WITH_FIELD_MATCHING_GETTERS = JSON_WITH_FORCE_ACCESS.with(
            JSON.Feature.USE_FIELD_MATCHING_GETTERS);

    @Test
    void collectsDeclaredConstructorsForDeserialization() throws Exception {
        BeanConstructors constructors = new BeanConstructors(IntrospectedBean.class);
        BeanPropertyIntrospector.addNonRecordConstructors(IntrospectedBean.class, constructors);
        POJODefinition definition = INTROSPECTOR.pojoDefinitionForDeserialization(JSON_READER, IntrospectedBean.class);

        IntrospectedBean objectBean = JSON_WITH_FORCE_ACCESS.beanFrom(IntrospectedBean.class,
                "{\"name\":\"Ada\",\"active\":true,\"visible\":7}");
        IntrospectedBean stringBean = JSON_WITH_FORCE_ACCESS.beanFrom(IntrospectedBean.class, "\"Ada\"");
        IntrospectedBean longBean = JSON_WITH_FORCE_ACCESS.beanFrom(IntrospectedBean.class, "7");

        assertThat(constructors).isNotNull();
        assertThat(definition.constructors()).isNotNull();
        assertThat(propertyNames(definition)).containsExactlyInAnyOrder("active", "name", "visible");
        assertThat(objectBean.getName()).isEqualTo("Ada");
        assertThat(objectBean.isActive()).isTrue();
        assertThat(objectBean.visible).isEqualTo(7);
        assertThat(stringBean.getName()).isEqualTo("Ada");
        assertThat(longBean.visible).isEqualTo(7);
    }

    @Test
    void collectsDeclaredFieldsAndMethodsForSerialization() throws Exception {
        POJODefinition definition = INTROSPECTOR.pojoDefinitionForSerialization(JSON_WRITER, IntrospectedBean.class);
        String json = JSON_WITH_FORCE_ACCESS.asString(IntrospectedBean.create("Ada", true, 7));

        assertThat(propertyNames(definition)).containsExactlyInAnyOrder("active", "name", "visible");
        assertThat(json).contains("\"name\":\"Ada\"", "\"active\":true", "\"visible\":7");

        POJODefinition.Prop visible = property(definition, "visible");
        POJODefinition.Prop name = property(definition, "name");
        POJODefinition.Prop active = property(definition, "active");

        assertThat(visible.field).isNotNull();
        assertThat(name.getter).isNotNull();
        assertThat(name.setter).isNotNull();
        assertThat(active.isGetter).isNotNull();
        assertThat(active.setter).isNotNull();
    }

    @Test
    void collectsIntegerConstructorsForNumericScalarDeserialization() throws Exception {
        BeanConstructors constructors = new BeanConstructors(IntConstructorBean.class);
        BeanPropertyIntrospector.addNonRecordConstructors(IntConstructorBean.class, constructors);
        POJODefinition definition = INTROSPECTOR.pojoDefinitionForDeserialization(JSON_READER, IntConstructorBean.class);

        IntConstructorBean bean = JSON.std.beanFrom(IntConstructorBean.class, "13");

        assertThat(constructors).isNotNull();
        assertThat(definition.constructors()).isNotNull();
        assertThat(propertyNames(definition)).contains("value");
        assertThat(bean.getValue()).isEqualTo(13);
    }

    @Test
    void supportsFieldNamedGettersWhenEnabled() throws Exception {
        String json = JSON_WITH_FIELD_MATCHING_GETTERS.asString(new FieldNamedGetterBean("Ada"));

        assertThat(json).contains("\"title\":\"Ada\"");
    }

    private static List<String> propertyNames(POJODefinition definition) {
        return definition.getProperties().stream().map(prop -> prop.name).toList();
    }

    private static POJODefinition.Prop property(POJODefinition definition, String name) {
        return definition.getProperties().stream()
                .filter(prop -> prop.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    static class BaseBean {
        public int visible;
    }

    static final class IntrospectedBean extends BaseBean {
        private String name;
        private boolean active;

        private IntrospectedBean() {
        }

        private IntrospectedBean(String name) {
            this.name = name;
        }

        private IntrospectedBean(long visible) {
            this.visible = (int) visible;
        }

        static IntrospectedBean create(String name, boolean active, int visible) {
            IntrospectedBean bean = new IntrospectedBean();
            bean.name = name;
            bean.active = active;
            bean.visible = visible;
            return bean;
        }

        public String getName() {
            return name;
        }

        private void setName(String name) {
            this.name = name;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    public static final class IntConstructorBean {
        private final int value;

        public IntConstructorBean(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    static final class FieldNamedGetterBean {
        private final String title;

        FieldNamedGetterBean(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }
}
