/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;

public class TypePoolInnerAbstractBaseInnerRawDescriptionArrayTest {
    private static final String ANNOTATION_TYPE = "net_bytebuddy.byte_buddy.TypePoolRawDescriptionArrayAnnotation";
    private static final String SAMPLE_TYPE = "net_bytebuddy.byte_buddy.TypePoolRawDescriptionArraySample";

    @Test
    void resolvesClassArrayAnnotationValueParsedByTypePool() {
        AnnotationValue<?, ?> annotationValue = classArrayAnnotationValue();

        TypeDescription[] resolved = (TypeDescription[]) annotationValue.resolve();

        assertThat(resolved).hasSize(2);
        assertThat(resolved[0].represents(String.class)).isTrue();
        assertThat(resolved[1].represents(Integer.class)).isTrue();
    }

    @Test
    void loadsClassArrayAnnotationValueParsedByTypePool() throws Exception {
        AnnotationValue<?, ?> annotationValue = classArrayAnnotationValue();

        AnnotationValue.Loaded<?> loaded = annotationValue.load(getClass().getClassLoader());

        assertThat(loaded.getState().isResolved()).isTrue();
        assertThat((Class<?>[]) loaded.resolve()).containsExactly(String.class, Integer.class);
    }

    private AnnotationValue<?, ?> classArrayAnnotationValue() {
        AnnotationDescription annotationDescription = parsedAnnotationDescription();
        MethodDescription.InDefinedShape property = annotationDescription.getAnnotationType()
                .getDeclaredMethods()
                .filter(named("types"))
                .getOnly();
        return annotationDescription.getValue(property);
    }

    private AnnotationDescription parsedAnnotationDescription() {
        ByteBuddy byteBuddy = new ByteBuddy(ClassFileVersion.JAVA_V8);
        DynamicType.Unloaded<?> annotationType = byteBuddy
                .makeAnnotation()
                .name(ANNOTATION_TYPE)
                .annotateType(AnnotationDescription.Builder.ofType(Retention.class)
                        .define("value", RetentionPolicy.RUNTIME)
                        .build())
                .defineMethod("types", Class[].class, Modifier.PUBLIC | Modifier.ABSTRACT)
                .withoutCode()
                .make();
        DynamicType.Unloaded<?> sampleType = byteBuddy
                .subclass(Object.class)
                .name(SAMPLE_TYPE)
                .annotateType(AnnotationDescription.Builder.ofType(annotationType.getTypeDescription())
                        .defineTypeArray("types", String.class, Integer.class)
                        .build())
                .make();
        Map<TypeDescription, byte[]> classFiles = new HashMap<TypeDescription, byte[]>();
        classFiles.putAll(annotationType.getAllTypes());
        classFiles.putAll(sampleType.getAllTypes());
        TypePool typePool = new TypePool.Default(
                new TypePool.CacheProvider.Simple(),
                ClassFileLocator.Simple.of(classFiles),
                TypePool.Default.ReaderMode.FAST,
                new LoadedTypesTypePool(
                        Annotation.class,
                        Class.class,
                        Integer.class,
                        Object.class,
                        Retention.class,
                        RetentionPolicy.class,
                        String.class));
        return typePool.describe(SAMPLE_TYPE)
                .resolve()
                .getDeclaredAnnotations()
                .getOnly();
    }

    private static class LoadedTypesTypePool extends TypePool.AbstractBase {
        private final Map<String, TypeDescription> typeDescriptions;

        LoadedTypesTypePool(Class<?>... types) {
            super(new TypePool.CacheProvider.Simple());
            typeDescriptions = new HashMap<String, TypeDescription>();
            for (Class<?> type : types) {
                typeDescriptions.put(type.getName(), TypeDescription.ForLoadedType.of(type));
            }
        }

        @Override
        protected Resolution doDescribe(String name) {
            TypeDescription typeDescription = typeDescriptions.get(name);
            return typeDescription == null
                    ? new Resolution.Illegal(name)
                    : new Resolution.Simple(typeDescription);
        }
    }
}
