/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package kotlinreflect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public final class ReflectJavaFixtures {
    private ReflectJavaFixtures() {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface JavaDetails {
        String name();

        int count() default 1;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(JavaTags.class)
    public @interface JavaTag {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface JavaTags {
        JavaTag[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.RECORD_COMPONENT, ElementType.METHOD, ElementType.PARAMETER})
    public @interface RecordDetail {
        String value();
    }

    @JavaDetails(name = "bean", count = 2)
    @JavaTag("alpha")
    @JavaTag("beta")
    public static class JavaBean {
        public String publicField = "java-field";
        private final String name;

        public JavaBean() {
            this("default");
        }

        public JavaBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String greet(String suffix) {
            return name + suffix;
        }

        public static final class Nested {
        }
    }

    public record JavaRecord(@RecordDetail("name-component") String name, int count) {
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(InheritedJavaTags.class)
    public @interface InheritedJavaTag {
        String value();
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface InheritedJavaTags {
        InheritedJavaTag[] value();
    }

    @InheritedJavaTag("parent-one")
    @InheritedJavaTag("parent-two")
    public static class TaggedParent {
    }

    public static final class TaggedChild extends TaggedParent {
    }

    public interface GenericValue<T> {
        T value();
    }

    public static final class StringValue implements GenericValue<String> {
        @Override
        public String value() {
            return "java-value";
        }
    }

    public sealed interface JavaSealed permits JavaSealedFirst, JavaSealedSecond {
    }

    public static final class JavaSealedFirst implements JavaSealed {
    }

    public static final class JavaSealedSecond implements JavaSealed {
    }

    public enum JavaEnum {
        FIRST,
        SECOND
    }
}
