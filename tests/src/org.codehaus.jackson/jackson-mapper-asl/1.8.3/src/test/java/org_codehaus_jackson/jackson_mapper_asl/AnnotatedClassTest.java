/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.introspect.AnnotatedField;
import org.codehaus.jackson.map.introspect.AnnotatedMethod;
import org.codehaus.jackson.map.introspect.BasicBeanDescription;
import org.codehaus.jackson.map.introspect.VisibilityChecker;
import org.codehaus.jackson.type.JavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedClassTest {
    @Test
    void introspectsCreatorsMemberMethodsFieldsAndMixIns() {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig serializationConfig = mapper.getSerializationConfig();
        DeserializationConfig deserializationConfig = mapper.getDeserializationConfig();
        serializationConfig.addMixInAnnotations(CreatorBackedBean.class, CreatorBackedBeanMixin.class);
        deserializationConfig.addMixInAnnotations(CreatorBackedBean.class, CreatorBackedBeanMixin.class);
        serializationConfig.addMixInAnnotations(Object.class, ObjectMixin.class);

        JavaType beanType = mapper.constructType(CreatorBackedBean.class);
        BasicBeanDescription serializationDescription = serializationConfig.introspect(beanType);
        BasicBeanDescription deserializationDescription = deserializationConfig.introspect(beanType);

        assertCreatorMetadataWasCollected(serializationDescription);
        assertMemberMethodsWereCollected(serializationConfig, serializationDescription, deserializationDescription);
        assertFieldsWereCollected(serializationConfig, deserializationConfig, serializationDescription, deserializationDescription);
    }

    private static void assertCreatorMetadataWasCollected(BasicBeanDescription description) {
        Constructor<?> defaultConstructor = description.findDefaultConstructor();
        Constructor<?> stringConstructor = description.findSingleArgConstructor(String.class);
        Method stringFactory = description.findFactoryMethod(String.class);

        assertThat(defaultConstructor).isNotNull();
        assertThat(stringConstructor).isNotNull();
        assertThat(stringFactory).isNotNull();
        assertThat(description.getConstructors()).isNotEmpty();
        assertThat(description.getFactoryMethods())
                .extracting(AnnotatedMethod::getName)
                .contains("valueOf");
    }

    private static void assertMemberMethodsWereCollected(
            SerializationConfig serializationConfig,
            BasicBeanDescription serializationDescription,
            BasicBeanDescription deserializationDescription) {
        VisibilityChecker<?> visibilityChecker = serializationConfig.getDefaultVisibilityChecker();
        LinkedHashMap<String, AnnotatedMethod> getters = serializationDescription.findGetters(
                visibilityChecker,
                Collections.<String>emptySet());
        LinkedHashMap<String, AnnotatedMethod> setters = deserializationDescription.findSetters(visibilityChecker);

        assertThat(getters).containsKeys("mixedName", "objectText");
        assertThat(setters).containsKey("mixedName");
        assertThat(serializationDescription.findMethod("getName", new Class<?>[0])).isNotNull();
    }

    private static void assertFieldsWereCollected(
            SerializationConfig serializationConfig,
            DeserializationConfig deserializationConfig,
            BasicBeanDescription serializationDescription,
            BasicBeanDescription deserializationDescription) {
        LinkedHashMap<String, AnnotatedField> serializableFields = serializationDescription.findSerializableFields(
                serializationConfig.getDefaultVisibilityChecker(),
                Collections.<String>emptySet());
        LinkedHashMap<String, AnnotatedField> deserializableFields = deserializationDescription.findDeserializableFields(
                deserializationConfig.getDefaultVisibilityChecker(),
                Collections.<String>emptySet());

        assertThat(serializableFields).containsKeys("mixedField", "parentField");
        assertThat(deserializableFields).containsKeys("mixedField", "parentField");
    }

    public static class BaseBean {
        public String parentField = "parent";

        public String getParentName() {
            return parentField;
        }
    }

    public static class CreatorBackedBean extends BaseBean {
        public String publicField;
        private String name;

        public CreatorBackedBean() {
            this("default");
        }

        @JsonCreator
        public CreatorBackedBean(@JsonProperty("name") String name) {
            this.name = name;
            this.publicField = "field-" + name;
        }

        public static CreatorBackedBean valueOf(String name) {
            return new CreatorBackedBean(name);
        }

        @JsonCreator
        public static CreatorBackedBean fromJson(@JsonProperty("name") String name) {
            return new CreatorBackedBean(name);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public abstract static class CreatorBackedBeanMixin {
        @JsonProperty("mixedField")
        public String publicField;

        public CreatorBackedBeanMixin() {
        }

        public CreatorBackedBeanMixin(@JsonProperty("name") String name) {
        }

        @JsonCreator
        public static CreatorBackedBean valueOf(@JsonProperty("name") String name) {
            return null;
        }

        @JsonProperty("mixedName")
        public abstract String getName();

        @JsonProperty("mixedName")
        public abstract void setName(String name);
    }

    public abstract static class ObjectMixin {
        @JsonProperty("objectText")
        public abstract String toString();
    }
}
