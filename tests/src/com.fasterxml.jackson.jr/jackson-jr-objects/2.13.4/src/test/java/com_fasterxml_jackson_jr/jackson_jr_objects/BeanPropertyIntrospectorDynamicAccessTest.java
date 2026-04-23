/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.List;

import com.fasterxml.jackson.jr.ob.api.CollectionBuilder;
import com.fasterxml.jackson.jr.ob.api.MapBuilder;
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

    @Test
    void collectsDeclaredConstructorsForDeserialization() {
        POJODefinition definition = INTROSPECTOR.pojoDefinitionForDeserialization(JSON_READER, IntrospectedBean.class);

        assertThat(definition.defaultCtor).isNotNull();
        assertThat(definition.stringCtor).isNotNull();
        assertThat(definition.longCtor).isNotNull();
        assertThat(propertyNames(definition)).containsExactlyInAnyOrder("active", "name", "visible");
    }

    @Test
    void collectsDeclaredFieldsAndMethodsForSerialization() {
        POJODefinition definition = INTROSPECTOR.pojoDefinitionForSerialization(JSON_WRITER, IntrospectedBean.class);

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
}
