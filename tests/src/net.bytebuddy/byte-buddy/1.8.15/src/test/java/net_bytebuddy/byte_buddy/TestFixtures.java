/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

import static net.bytebuddy.matcher.ElementMatchers.named;

public final class TestFixtures {
    private TestFixtures() {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
    public @interface Mark {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Label {
        String value();
    }

    public enum Mode {
        READ, WRITE
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Options {
        Label nested();
        Mode mode();
        Class<?> type();
        Class<?>[] types();
        Mode[] modes();
        Label[] labels();
    }

    @Label("catalog")
    @Options(nested = @Label("nested"), mode = Mode.READ, type = String.class,
            types = {String.class, Integer.class}, modes = {Mode.READ, Mode.WRITE},
            labels = {@Label("first"), @Label("second")})
    public static class Annotated {
    }

    public static class Parent {
        public int count;
    }

    public static class Owner<T> {
        public class Member<U> {
        }
    }

    public static class Sample<T extends @Mark("bound") Number>
            extends @Mark("super") Parent implements @Mark("interface") Runnable {
        public @Mark("field") String title;
        public List<@Mark("argument") String> names;
        public List<List<@Mark("deep") String[]>> nested;
        public List<? extends @Mark("upper") Number> upper;
        public List<? super @Mark("lower") Integer> lower;
        public @Mark("component") String[] array;
        public T variable;
        public @Mark("owner") Owner<String>.Member<Integer> member;

        public Sample() {
        }

        public Sample(String input, long count) {
            title = input;
            this.count = (int) count;
        }

        public @Mark("return") String transform(@Mark("receiver") Sample<T> this,
                                               @Mark("parameter") String input) throws @Mark("exception") IOException {
            return input;
        }

        public void accept(@Label("injected") String input) {
            title = input;
        }

        @Override
        public void run() {
            count++;
        }
    }

    public static TypeDescription sample() {
        return TypeDescription.ForLoadedType.of(Sample.class);
    }

    public static TypeDescription.Generic field(String name) {
        return sample().getDeclaredFields().filter(named(name)).getOnly().getType();
    }

    public static MethodDescription.InDefinedShape method(String name) {
        return sample().getDeclaredMethods().filter(named(name)).getOnly();
    }

    public static MethodDescription.InDefinedShape property(Class<? extends Annotation> type, String name) {
        return TypeDescription.ForLoadedType.of(type).getDeclaredMethods().filter(named(name)).getOnly();
    }

    @SuppressWarnings("annotationAccess")
    public static String mark(TypeDescription.Generic type) {
        return type.getDeclaredAnnotations().ofType(Mark.class).loadSilent().value();
    }

    @SuppressWarnings("annotationAccess")
    public static AnnotationDescription options() {
        return TypeDescription.ForLoadedType.of(Annotated.class).getDeclaredAnnotations().ofType(Options.class);
    }

    public static TypePool libraryPool() {
        TypePool.CacheProvider cache = TypePool.CacheProvider.Simple.withObjectType();
        for (Class<?> type : List.of(Class.class, Enum.class, Annotation.class, Documented.class,
                Retention.class, Target.class, RetentionPolicy.class, ElementType.class)) {
            cache.register(type.getName(), new TypePool.Resolution.Simple(TypeDescription.ForLoadedType.of(type)));
        }
        return new TypePool.Default(cache, ClassFileLocator.ForClassLoader.of(TestFixtures.class.getClassLoader()),
                TypePool.Default.ReaderMode.FAST);
    }
}
