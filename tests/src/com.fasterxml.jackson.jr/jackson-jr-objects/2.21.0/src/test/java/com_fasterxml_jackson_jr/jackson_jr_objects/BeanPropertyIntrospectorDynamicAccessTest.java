/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.lang.reflect.Constructor;
import java.util.List;

import com.fasterxml.jackson.jr.ob.impl.BeanConstructors;
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyIntrospector;
import com.fasterxml.jackson.jr.ob.impl.JSONWriter;
import com.fasterxml.jackson.jr.ob.impl.POJODefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyIntrospectorDynamicAccessTest {
    private static final BeanPropertyIntrospector INTROSPECTOR = BeanPropertyIntrospector.instance();
    private static final JSONWriter JSON_WRITER = new JSONWriter();

    @Test
    void collectsDeclaredConstructorsForNonRecordBeans() {
        InspectableBeanConstructors constructors = new InspectableBeanConstructors(ConstructorIntrospectedBean.class);

        BeanPropertyIntrospector.addNonRecordConstructors(ConstructorIntrospectedBean.class, constructors);

        assertThat(constructors.noArgsConstructor()).isNotNull();
        assertThat(constructors.stringConstructor()).isNotNull();
        assertThat(constructors.longConstructor()).isNotNull();
        assertThat(constructors.intConstructor()).isNull();
    }

    @Test
    void collectsDeclaredFieldsAndMethodsForSerialization() {
        POJODefinition definition = INTROSPECTOR.pojoDefinitionForSerialization(JSON_WRITER, FieldAndMethodIntrospectedBean.class);

        assertThat(propertyNames(definition)).containsExactlyInAnyOrder("active", "name", "visible");

        POJODefinition.Prop visible = property(definition, "visible");
        POJODefinition.Prop name = property(definition, "name");
        POJODefinition.Prop active = property(definition, "active");

        assertThat(visible.field).isNotNull();
        assertThat(name.getter).isNotNull();
        assertThat(name.setter).isNotNull();
        assertThat(active.isGetter).isNotNull();
        assertThat(active.setter).isNotNull();
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

    public static final class ConstructorIntrospectedBean {
        public ConstructorIntrospectedBean() {
        }

        public ConstructorIntrospectedBean(String value) {
        }

        public ConstructorIntrospectedBean(long value) {
        }
    }

    public static class FieldBaseBean {
        public int visible;
    }

    public static final class FieldAndMethodIntrospectedBean extends FieldBaseBean {
        public static int ignoredStatic = 12;
        public static final int IGNORED_CONSTANT = 13;

        private String name;
        private boolean active;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    public static final class InspectableBeanConstructors extends BeanConstructors {
        public InspectableBeanConstructors(Class<?> valueType) {
            super(valueType);
        }

        public Constructor<?> noArgsConstructor() {
            return _noArgsCtor;
        }

        public Constructor<?> stringConstructor() {
            return _stringCtor;
        }

        public Constructor<?> longConstructor() {
            return _longCtor;
        }

        public Constructor<?> intConstructor() {
            return _intCtor;
        }
    }
}
