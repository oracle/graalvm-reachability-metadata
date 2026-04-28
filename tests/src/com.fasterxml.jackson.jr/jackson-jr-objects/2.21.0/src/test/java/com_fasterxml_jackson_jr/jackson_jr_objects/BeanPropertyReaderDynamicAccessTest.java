/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.fasterxml.jackson.jr.ob.JacksonJrExtension;
import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.api.ExtensionContext;
import com.fasterxml.jackson.jr.ob.api.ReaderWriterModifier;
import com.fasterxml.jackson.jr.ob.impl.BeanConstructors;
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyIntrospector;
import com.fasterxml.jackson.jr.ob.impl.JSONReader;
import com.fasterxml.jackson.jr.ob.impl.POJODefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyReaderDynamicAccessTest {
    private static final JSON JSON_WITH_FORCE_ACCESS = JSON.std.with(JSON.Feature.FORCE_REFLECTION_ACCESS);
    private static final JSON MODIFIER_AWARE_JSON = JSON.builder()
            .enable(JSON.Feature.FORCE_REFLECTION_ACCESS)
            .register(new BeanPropertyReaderDefinitionExtension())
            .build();

    @Test
    void populatesModifierDefinedFieldBackedProperties() throws Exception {
        FieldBackedReaderBean bean = MODIFIER_AWARE_JSON.beanFrom(FieldBackedReaderBean.class,
                "{\"name\":\"Ada\"}");

        assertThat(bean.getName()).isEqualTo("Ada");
    }

    @Test
    void populatesPublicSetterBackedProperties() throws Exception {
        PublicSetterBackedReaderBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(PublicSetterBackedReaderBean.class,
                "{\"name\":\"Ada\"}");

        assertThat(bean.getName()).isEqualTo("Ada");
        assertThat(bean.getSetterCalls()).isEqualTo(1);
    }

    @Test
    void populatesModifierDefinedPrivateSetterBackedProperties() throws Exception {
        SetterBackedReaderBean bean = MODIFIER_AWARE_JSON.beanFrom(SetterBackedReaderBean.class,
                "{\"name\":\"Ada\"}");

        assertThat(bean.getName()).isEqualTo("Ada");
        assertThat(bean.getSetterCalls()).isEqualTo(1);
    }

    public static final class FieldBackedReaderBean {
        private String name;

        public FieldBackedReaderBean() {
        }

        public String getName() {
            return name;
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

    public static final class BeanPropertyReaderDefinitionExtension extends JacksonJrExtension {
        @Override
        protected void register(ExtensionContext ctxt) {
            ctxt.insertModifier(new BeanPropertyReaderDefinitionModifier());
        }
    }

    public static final class BeanPropertyReaderDefinitionModifier extends ReaderWriterModifier {
        @Override
        public POJODefinition pojoDefinitionForDeserialization(JSONReader readContext, Class<?> pojoType) {
            if (pojoType == FieldBackedReaderBean.class) {
                return new POJODefinition(FieldBackedReaderBean.class,
                        new POJODefinition.Prop[] {
                                new POJODefinition.Prop("name", "name",
                                        declaredField(FieldBackedReaderBean.class, "name"),
                                        null, null, null, null)
                        },
                        constructorsFor(FieldBackedReaderBean.class));
            }
            if (pojoType == SetterBackedReaderBean.class) {
                return new POJODefinition(SetterBackedReaderBean.class,
                        new POJODefinition.Prop[] {
                                new POJODefinition.Prop("name", "name", null,
                                        declaredMethod(SetterBackedReaderBean.class, "setName", String.class),
                                        null, null, null)
                        },
                        constructorsFor(SetterBackedReaderBean.class));
            }
            return null;
        }

        private static BeanConstructors constructorsFor(Class<?> beanType) {
            BeanConstructors constructors = new BeanConstructors(beanType);
            BeanPropertyIntrospector.addNonRecordConstructors(beanType, constructors);
            return constructors;
        }

        private static Field declaredField(Class<?> declaringType, String fieldName) {
            try {
                return declaringType.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private static Method declaredMethod(Class<?> declaringType, String methodName,
                Class<?>... parameterTypes) {
            try {
                return declaringType.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
