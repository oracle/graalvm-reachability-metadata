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

public class TypePoolInnerAbstractBaseInnerRawAnnotationValueTest {
    private static final String OUTER_ANNOTATION_TYPE =
            "net_bytebuddy.byte_buddy.TypePoolRawAnnotationOuterAnnotation";
    private static final String SAMPLE_TYPE = "net_bytebuddy.byte_buddy.TypePoolRawAnnotationSample";

    @Test
    void loadsNestedAnnotationValueParsedByTypePool() throws Exception {
        AnnotationValue<?, ?> annotationValue = nestedAnnotationValue();

        AnnotationValue.Loaded<?> loaded = annotationValue.load(getClass().getClassLoader());
        NestedAnnotation annotation = (NestedAnnotation) loaded.resolve();

        assertThat(loaded.getState().isResolved()).isTrue();
        assertThat(annotation.annotationType()).isEqualTo(NestedAnnotation.class);
        assertThat(annotation.value()).isEqualTo("byte-buddy");
    }

    @Test
    void resolvesNestedAnnotationValueParsedByTypePool() {
        AnnotationValue<?, ?> annotationValue = nestedAnnotationValue();

        AnnotationDescription annotationDescription = (AnnotationDescription) annotationValue.resolve();

        MethodDescription.InDefinedShape property = annotationDescription.getAnnotationType()
                .getDeclaredMethods()
                .filter(named("value"))
                .getOnly();

        assertThat(annotationDescription.getAnnotationType().represents(NestedAnnotation.class)).isTrue();
        assertThat(annotationDescription.getValue(property).resolve()).isEqualTo("byte-buddy");
    }

    private AnnotationValue<?, ?> nestedAnnotationValue() {
        AnnotationDescription annotationDescription = parsedOuterAnnotationDescription();
        MethodDescription.InDefinedShape property = annotationDescription.getAnnotationType()
                .getDeclaredMethods()
                .filter(named("nested"))
                .getOnly();
        return annotationDescription.getValue(property);
    }

    private AnnotationDescription parsedOuterAnnotationDescription() {
        ByteBuddy byteBuddy = new ByteBuddy(ClassFileVersion.JAVA_V8);
        DynamicType.Unloaded<?> outerAnnotationType = byteBuddy
                .makeAnnotation()
                .name(OUTER_ANNOTATION_TYPE)
                .annotateType(AnnotationDescription.Builder.ofType(Retention.class)
                        .define("value", RetentionPolicy.RUNTIME)
                        .build())
                .defineMethod("nested", NestedAnnotation.class, Modifier.PUBLIC | Modifier.ABSTRACT)
                .withoutCode()
                .make();
        AnnotationDescription nestedAnnotation = AnnotationDescription.Builder
                .ofType(NestedAnnotation.class)
                .define("value", "byte-buddy")
                .build();
        DynamicType.Unloaded<?> sampleType = byteBuddy
                .subclass(Object.class)
                .name(SAMPLE_TYPE)
                .annotateType(AnnotationDescription.Builder.ofType(outerAnnotationType.getTypeDescription())
                        .define("nested", nestedAnnotation)
                        .build())
                .make();
        Map<TypeDescription, byte[]> classFiles = new HashMap<TypeDescription, byte[]>();
        classFiles.putAll(outerAnnotationType.getAllTypes());
        classFiles.putAll(sampleType.getAllTypes());
        TypePool typePool = new TypePool.Default(
                new TypePool.CacheProvider.Simple(),
                ClassFileLocator.Simple.of(classFiles),
                TypePool.Default.ReaderMode.FAST,
                new LoadedTypesTypePool(
                        Annotation.class,
                        NestedAnnotation.class,
                        Object.class,
                        Retention.class,
                        RetentionPolicy.class,
                        String.class));
        return typePool.describe(SAMPLE_TYPE)
                .resolve()
                .getDeclaredAnnotations()
                .getOnly();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NestedAnnotation {
        String value();
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
