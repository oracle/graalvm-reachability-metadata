/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ClassIntrospector.MixInResolver;
import org.codehaus.jackson.map.introspect.AnnotatedClass;
import org.codehaus.jackson.map.introspect.AnnotatedConstructor;
import org.codehaus.jackson.map.introspect.AnnotatedField;
import org.codehaus.jackson.map.introspect.AnnotatedMethod;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.junit.jupiter.api.Test;

public class AnnotatedClassTest {
    private static final AnnotationIntrospector INTROSPECTOR = new JacksonAnnotationIntrospector();
    private static final Class<?>[] NO_PARAMETERS = new Class<?>[0];
    private static final Class<?>[] STRING_PARAMETER = new Class<?>[] {String.class};

    @Test
    public void resolvesCreatorsMemberMethodsAndFieldsWithMixIns() {
        AnnotatedClass annotatedClass = AnnotatedClass.construct(
                CreatorTarget.class, INTROSPECTOR, new TargetMixInResolver());

        annotatedClass.resolveCreators(true);
        annotatedClass.resolveMemberMethods(null, true);
        annotatedClass.resolveFields(true);

        AnnotatedConstructor defaultConstructor = annotatedClass.getDefaultConstructor();
        assertThat(defaultConstructor).isNotNull();
        assertThat((defaultConstructor).getAnnotation(JsonCreator.class)).isNotNull();

        AnnotatedConstructor stringConstructor = annotatedClass.getConstructors().get(0);
        assertThat(stringConstructor.getParameterClass(0)).isEqualTo(String.class);
        assertThat(stringConstructor.getParameter(0).getAnnotation(JsonProperty.class).value()).isEqualTo("ctorName");

        AnnotatedMethod factory = findStaticMethod(annotatedClass, "fromName");
        assertThat(factory.getParameter(0).getAnnotation(JsonProperty.class).value()).isEqualTo("factoryName");

        AnnotatedMethod getter = annotatedClass.findMethod("getName", NO_PARAMETERS);
        assertThat((getter).getAnnotation(JsonProperty.class).value()).isEqualTo("mixedName");

        AnnotatedMethod inheritedSetter = annotatedClass.findMethod("setBaseName", STRING_PARAMETER);
        assertThat(inheritedSetter).isNotNull();

        AnnotatedMethod objectHashCode = annotatedClass.findMethod("hashCode", NO_PARAMETERS);
        assertThat(objectHashCode.getDeclaringClass()).isEqualTo(Object.class);
        assertThat((objectHashCode).getAnnotation(JsonProperty.class).value()).isEqualTo("objectHashCode");

        AnnotatedField mixedField = findField(annotatedClass, "fieldName");
        assertThat((mixedField).getAnnotation(JsonProperty.class).value()).isEqualTo("mixedFieldName");

        AnnotatedField inheritedField = findField(annotatedClass, "baseName");
        assertThat(inheritedField).isNotNull();
    }

    private static AnnotatedMethod findStaticMethod(AnnotatedClass annotatedClass, String name) {
        for (AnnotatedMethod method : annotatedClass.getStaticMethods()) {
            if (name.equals(method.getName())) {
                return method;
            }
        }
        throw new AssertionError("No static method named " + name);
    }

    private static AnnotatedField findField(AnnotatedClass annotatedClass, String name) {
        for (AnnotatedField field : annotatedClass.fields()) {
            if (name.equals(field.getName())) {
                return field;
            }
        }
        throw new AssertionError("No field named " + name);
    }

    private static final class TargetMixInResolver implements MixInResolver {
        @Override
        public Class<?> findMixInClassFor(Class<?> cls) {
            if (cls == CreatorTarget.class) {
                return CreatorTargetMixIn.class;
            }
            if (cls == Object.class) {
                return ObjectMixIn.class;
            }
            return null;
        }
    }

    private static class BaseTarget {
        public String baseName;

        public void setBaseName(String baseName) {
            this.baseName = baseName;
        }
    }

    private static final class CreatorTarget extends BaseTarget {
        public String fieldName;
        private final String name;

        public CreatorTarget() {
            this("default");
        }

        public CreatorTarget(String name) {
            this.name = name;
        }

        public static CreatorTarget fromName(String name) {
            return new CreatorTarget(name);
        }

        public String getName() {
            return name;
        }
    }

    private abstract static class CreatorTargetMixIn {
        @JsonProperty("mixedFieldName")
        public String fieldName;

        @JsonCreator
        public CreatorTargetMixIn() {
        }

        @JsonCreator
        public CreatorTargetMixIn(@JsonProperty("ctorName") String name) {
        }

        public static CreatorTarget fromName(@JsonProperty("factoryName") String name) {
            return null;
        }

        @JsonProperty("mixedName")
        public abstract String getName();
    }

    private abstract static class ObjectMixIn {
        @Override
        @JsonProperty("objectHashCode")
        public abstract int hashCode();
    }
}
