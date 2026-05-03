/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ClassIntrospector.MixInResolver;
import org.codehaus.jackson.map.introspect.AnnotatedClass;
import org.codehaus.jackson.map.introspect.AnnotatedField;
import org.codehaus.jackson.map.introspect.AnnotatedMethod;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.jackson.map.introspect.MethodFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedClassTest {
    private static final AnnotationIntrospector INTROSPECTOR = new JacksonAnnotationIntrospector();
    private static final MethodFilter INCLUDE_ALL_METHODS = method -> true;
    private static final MixInResolver MIX_INS = new TestMixInResolver();

    @Test
    void resolvesCreatorsWithConstructorAndFactoryMixIns() {
        AnnotatedClass annotatedClass = AnnotatedClass.construct(CreatorBean.class, INTROSPECTOR, MIX_INS);

        annotatedClass.resolveCreators(true);

        assertThat(annotatedClass.getDefaultConstructor()).isNotNull();
        assertThat(annotatedClass.getConstructors()).isNotEmpty();
        assertThat(annotatedClass.getStaticMethods())
                .extracting(AnnotatedMethod::getName)
                .contains("fromText");
    }

    @Test
    void resolvesMemberMethodsWithPrimarySupertypeAndObjectMixIns() {
        AnnotatedClass annotatedClass = AnnotatedClass.construct(CreatorBean.class, INTROSPECTOR, MIX_INS);

        annotatedClass.resolveMemberMethods(INCLUDE_ALL_METHODS, true);

        assertThat(annotatedClass.getMemberMethodCount()).isGreaterThan(0);
        assertThat(annotatedClass.findMethod("getName", new Class<?>[0])).isNotNull();
        assertThat(annotatedClass.findMethod("hashCode", new Class<?>[0])).isNotNull();
    }

    @Test
    void resolvesFieldsWithFieldMixInsForClassHierarchy() {
        AnnotatedClass annotatedClass = AnnotatedClass.construct(CreatorBean.class, INTROSPECTOR, MIX_INS);

        annotatedClass.resolveFields(true);

        assertThat(annotatedClass.getFieldCount()).isGreaterThanOrEqualTo(3);
        assertThat(annotatedClass.fields())
                .extracting(AnnotatedField::getName)
                .contains("baseName", "name", "count");
    }

    private static final class TestMixInResolver implements MixInResolver {
        @Override
        public Class<?> findMixInClassFor(Class<?> cls) {
            if (cls == CreatorBean.class) {
                return CreatorBeanMixIn.class;
            }
            if (cls == CreatorBase.class) {
                return CreatorBaseMixIn.class;
            }
            if (cls == Object.class) {
                return ObjectMixIn.class;
            }
            return null;
        }
    }

    public static class CreatorBase {
        public String baseName = "base";

        public String getBaseName() {
            return baseName;
        }
    }

    public static class CreatorBean extends CreatorBase {
        public String name;
        public int count;
        private String secret = "secret";

        public CreatorBean() {
            this("default", 0);
        }

        public CreatorBean(int count) {
            this("number", count);
        }

        private CreatorBean(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public static CreatorBean fromText(String text) {
            return new CreatorBean(text, text.length());
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSecret() {
            return secret;
        }
    }

    public abstract static class CreatorBaseMixIn {
        @JsonProperty("baseName")
        public String baseName;
    }

    public abstract static class CreatorBeanMixIn {
        @JsonProperty("name")
        public String name;

        @JsonProperty("count")
        public int count;

        @JsonCreator
        CreatorBeanMixIn() {
        }

        @JsonCreator
        CreatorBeanMixIn(@JsonProperty("count") int count) {
        }

        @JsonCreator
        public static CreatorBeanMixIn fromText(@JsonProperty("text") String text) {
            return null;
        }

        @JsonProperty("name")
        public abstract String getName();
    }

    public abstract static class ObjectMixIn {
        @Override
        @JsonProperty("hash")
        public abstract int hashCode();
    }
}
