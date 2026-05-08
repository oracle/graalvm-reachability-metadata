/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye.jandex_gizmo2;

import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.constant.ClassDesc;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.ArrayType;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;
import org.jboss.jandex.TypeVariable;
import org.jboss.jandex.VoidType;
import org.jboss.jandex.WildcardType;
import org.jboss.jandex.gizmo2.Jandex2Gizmo;
import org.jboss.jandex.gizmo2.StringBuilderGen;
import org.junit.jupiter.api.Test;

import io.quarkus.gizmo2.ClassOutput;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.GenericType;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.TypeArgument;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.MethodDesc;

public class Jandex_gizmo2Test {
    private static final ClassDesc SAMPLE_DESC = ClassDesc.of("io_smallrye.jandex_gizmo2.JandexGizmoSample");
    private static final ClassDesc STATUS_DESC = ClassDesc.of("io_smallrye.jandex_gizmo2.JandexGizmoStatus");
    private static final ClassDesc STRING_DESC = ClassDesc.of("java.lang.String");
    private static final ClassDesc LIST_DESC = ClassDesc.of("java.util.List");
    private static final ClassDesc MAP_DESC = ClassDesc.of("java.util.Map");
    private static final ClassDesc NUMBER_DESC = ClassDesc.of("java.lang.Number");
    private static final ClassDesc IO_EXCEPTION_DESC = ClassDesc.of("java.io.IOException");

    @Test
    void convertsDotNamesAndJandexTypesToClassDescriptors() {
        assertThat(Jandex2Gizmo.classDescOf(DotName.createSimple("int"))).isEqualTo(CD_int);
        assertThat(Jandex2Gizmo.classDescOf(DotName.createSimple("void"))).isEqualTo(CD_void);
        assertThat(Jandex2Gizmo.classDescOf(DotName.createSimple("java.lang.String"))).isEqualTo(STRING_DESC);
        assertThat(Jandex2Gizmo.classDescOf(DotName.createSimple("[[I"))).isEqualTo(ClassDesc.ofDescriptor("[[I"));
        assertThat(Jandex2Gizmo.classDescOf(DotName.createSimple("[[Ljava.lang.String;")))
                .isEqualTo(STRING_DESC.arrayType(2));

        assertThat(Jandex2Gizmo.classDescOf(PrimitiveType.BOOLEAN)).isEqualTo(ClassDesc.ofDescriptor("Z"));
        assertThat(Jandex2Gizmo.classDescOf(VoidType.VOID)).isEqualTo(CD_void);
        assertThat(Jandex2Gizmo.classDescOf(ArrayType.create(ClassType.STRING_TYPE, 3)))
                .isEqualTo(STRING_DESC.arrayType(3));
    }

    @Test
    void createsFieldMethodConstructorAndEnumDescriptorsFromIndexedClasses() throws IOException {
        Index index = sampleIndex();
        ClassInfo sample = requireClass(index, "io_smallrye.jandex_gizmo2.JandexGizmoSample");

        FieldDesc names = Jandex2Gizmo.fieldDescOf(sample.field("names"));
        assertThat(names.owner()).isEqualTo(SAMPLE_DESC);
        assertThat(names.name()).isEqualTo("names");
        assertThat(names.type()).isEqualTo(LIST_DESC);

        MethodInfo transform = sample.firstMethod("transform");
        MethodDesc transformDesc = Jandex2Gizmo.methodDescOf(transform);
        assertThat(transformDesc.owner()).isEqualTo(SAMPLE_DESC);
        assertThat(transformDesc.name()).isEqualTo("transform");
        assertThat(transformDesc.returnType()).isEqualTo(MAP_DESC);
        assertThat(transformDesc.parameterTypes()).containsExactly(LIST_DESC, CD_int.arrayType());

        ConstructorDesc constructorDesc = Jandex2Gizmo.constructorDescOf(sample.constructors().get(0));
        assertThat(constructorDesc.owner()).isEqualTo(SAMPLE_DESC);
        assertThat(constructorDesc.parameterTypes()).containsExactly(CD_int, STRING_DESC);

        ClassInfo status = requireClass(index, "io_smallrye.jandex_gizmo2.JandexGizmoStatus");
        assertThat(Jandex2Gizmo.constOfEnum(status.field("READY")).desc())
                .isEqualTo(Enum.EnumDesc.of(STATUS_DESC, "READY"));
    }

    @Test
    void convertsGenericClassesArraysTypeVariablesAndWildcards() throws IOException {
        Type listOfString = ParameterizedType.create(List.class, ClassType.STRING_TYPE);
        GenericType.OfClass listType = Jandex2Gizmo.genericTypeOfClass(listOfString);
        assertThat(listType.desc()).isEqualTo(LIST_DESC);
        assertThat(listType.typeArguments()).hasSize(1);
        assertThat(listType.typeArguments().get(0).toString()).contains("java.lang.String");

        Type mapWithWildcards = ParameterizedType.create(Map.class,
                WildcardType.createUpperBound(ClassType.create(Number.class)),
                WildcardType.createLowerBound(ClassType.STRING_TYPE));
        GenericType.OfClass mapType = Jandex2Gizmo.genericTypeOfClass(mapWithWildcards);
        List<String> wildcardArguments = mapType.typeArguments().stream().map(Object::toString).toList();
        assertThat(mapType.desc()).isEqualTo(MAP_DESC);
        assertThat(wildcardArguments)
                .anySatisfy(argument -> assertThat(argument).contains("extends").contains("java.lang.Number"))
                .anySatisfy(argument -> assertThat(argument).contains("super").contains("java.lang.String"));

        GenericType.OfArray arrayType = Jandex2Gizmo.genericTypeOfArray(ArrayType.create(listOfString, 2));
        assertThat(arrayType.desc()).isEqualTo(LIST_DESC.arrayType(2));
        assertThat(arrayType.componentType().desc()).isEqualTo(LIST_DESC.arrayType());

        TypeVariable typeVariable = sampleIndex()
                .getClassByName("io_smallrye.jandex_gizmo2.JandexGizmoSample")
                .typeParameters()
                .get(0);
        GenericType.OfTypeVariable gizmoTypeVariable = Jandex2Gizmo.genericTypeOfTypeVariable(typeVariable);
        assertThat(gizmoTypeVariable.name()).isEqualTo("T");
        assertThat(gizmoTypeVariable.desc()).isEqualTo(NUMBER_DESC);

        TypeArgument unbounded = Jandex2Gizmo.typeArgumentOf(WildcardType.UNBOUNDED);
        assertThat(unbounded.toString()).contains("?");
    }

    @Test
    void convertsGenericThrowsClausesAndMethodTypeParameters() throws IOException {
        Index index = sampleIndex();
        ClassInfo sample = requireClass(index, "io_smallrye.jandex_gizmo2.JandexGizmoSample");
        MethodInfo risky = sample.firstMethod("risky");

        GenericType.OfThrows throwsType = Jandex2Gizmo.genericTypeOfThrows(risky.exceptions().get(0), index);
        assertThat(throwsType.desc()).isEqualTo(IO_EXCEPTION_DESC);
        assertThat(throwsType.toString()).contains("E");
        assertThat(throwsType.hasVisibleAnnotations()).isTrue();

        Map<String, byte[]> generatedClasses = new LinkedHashMap<>();
        Gizmo.create(generatedClasses::put).class_("io.smallrye.generated.CopiedThrowsMetadata", cc -> {
            cc.abstract_();
            cc.abstractMethod("copiedRisky", mc -> {
                Jandex2Gizmo.copyModifiers(risky, mc);
                Jandex2Gizmo.copyTypeParameters(risky, mc);
                mc.parameter("value", Jandex2Gizmo.genericTypeOf(risky.parameterType(0), index));
                mc.returning(Jandex2Gizmo.genericTypeOf(risky.returnType(), index));
                mc.throws_(throwsType);
            });
        });

        assertThat(generatedClasses).hasSize(1);
        assertThat(generatedClasses.values()).allSatisfy(Jandex_gizmo2Test::assertClassFile);
    }

    @Test
    void preservesRuntimeVisibleAndClassRetentionTypeAnnotations() throws IOException {
        Index index = sampleIndex();
        ClassInfo sample = requireClass(index, "io_smallrye.jandex_gizmo2.JandexGizmoSample");

        GenericType visibleType = Jandex2Gizmo.genericTypeOf(sample.field("visibleName").type(), index);
        assertThat(visibleType.hasVisibleAnnotations()).isTrue();
        assertThat(visibleType.hasInvisibleAnnotations()).isFalse();

        GenericType invisibleType = Jandex2Gizmo.genericTypeOf(sample.field("invisibleName").type(), index);
        assertThat(invisibleType.hasInvisibleAnnotations()).isTrue();
        assertThat(invisibleType.hasVisibleAnnotations()).isFalse();
    }

    @Test
    void copiesAnnotationsTypeParametersModifiersAndFieldTypesIntoGeneratedBytecode() throws IOException {
        Index index = sampleIndex();
        ClassInfo sample = requireClass(index, "io_smallrye.jandex_gizmo2.JandexGizmoSample");
        FieldInfo namesField = sample.field("names");
        FieldInfo copiedAnnotationField = sample.field("copiedAnnotation");
        FieldInfo constantField = sample.field("CONSTANT");
        Map<String, byte[]> generatedClasses = new LinkedHashMap<>();

        Gizmo.create(generatedClasses::put).class_("io.smallrye.generated.CopiedJandexMetadata", cc -> {
            Jandex2Gizmo.copyAnnotations(copiedAnnotationField, index).accept(cc);
            Jandex2Gizmo.copyTypeParameters(sample, cc);

            cc.field("copiedNames", fc -> {
                fc.setType(Jandex2Gizmo.genericTypeOf(namesField.type(), index));
                Jandex2Gizmo.copyModifiers(namesField, fc);
                Jandex2Gizmo.copyAnnotations(namesField, index).accept(fc);
            });
            cc.staticField("copiedConstant", fc -> {
                fc.setType(Jandex2Gizmo.classDescOf(constantField.type()));
                Jandex2Gizmo.copyModifiers(constantField, fc);
                fc.setInitial("copied");
            });
        });

        assertThat(generatedClasses).hasSize(1);
        assertThat(generatedClasses.values()).allSatisfy(Jandex_gizmo2Test::assertClassFile);
    }

    @Test
    void stringBuilderGeneratorEmitsAppendCodeWithoutLoadingGeneratedClasses() {
        Map<String, byte[]> generatedClasses = new LinkedHashMap<>();
        ClassOutput output = generatedClasses::put;

        Gizmo.create(output).class_("io.smallrye.generated.GeneratedStringBuilderUser", cc -> {
            cc.staticMethod("message", mc -> {
                mc.returning(String.class);
                mc.body(bc -> {
                    StringBuilderGen builder = StringBuilderGen.ofNew(32, bc)
                            .append("answer=")
                            .append(Const.of(42))
                            .append(',')
                            .appendCodePoint(32)
                            .append(bc.newArray(char.class, Const.of('o'), Const.of('k')));
                    builder.setLength(12);
                    bc.return_(builder.append('!').toString_());
                });
            });
        });

        assertThat(generatedClasses).hasSize(1);
        assertThat(generatedClasses.values()).allSatisfy(Jandex_gizmo2Test::assertClassFile);
    }

    @Test
    void rejectsJandexObjectsThatDoNotMatchRequestedGizmoDescriptorKind() throws IOException {
        Index index = sampleIndex();
        ClassInfo sample = requireClass(index, "io_smallrye.jandex_gizmo2.JandexGizmoSample");
        MethodInfo constructor = sample.constructors().get(0);
        MethodInfo method = sample.firstMethod("transform");

        assertThatThrownBy(() -> Jandex2Gizmo.methodDescOf(constructor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("constructor");
        assertThatThrownBy(() -> Jandex2Gizmo.constructorDescOf(method))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regular method");
        assertThatThrownBy(() -> Jandex2Gizmo.genericTypeOfReference(PrimitiveType.INT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reference");
        assertThatThrownBy(() -> Jandex2Gizmo.genericTypeOfPrimitive(ClassType.STRING_TYPE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primitive");
        assertThatThrownBy(() -> Jandex2Gizmo.typeArgumentOf(PrimitiveType.INT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Primitive types");
        assertThatThrownBy(() -> Jandex2Gizmo.constOfEnum(sample.field("count")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not an enum constant");
    }

    private static Index sampleIndex() throws IOException {
        return Index.of(JandexGizmoSample.class, JandexGizmoStatus.class, RuntimeMarker.class,
                ClassRetentionMarker.class, FieldOnlyMarker.class, NestedRuntimeMarker.class);
    }

    private static ClassInfo requireClass(Index index, String name) {
        ClassInfo classInfo = index.getClassByName(name);
        assertThat(classInfo).isNotNull();
        return classInfo;
    }

    private static void assertClassFile(byte[] bytes) {
        assertThat(bytes).hasSizeGreaterThan(32);
        assertThat(bytes[0]).isEqualTo((byte) 0xCA);
        assertThat(bytes[1]).isEqualTo((byte) 0xFE);
        assertThat(bytes[2]).isEqualTo((byte) 0xBA);
        assertThat(bytes[3]).isEqualTo((byte) 0xBE);
    }
}

@RuntimeMarker(
        value = 9,
        enabled = true,
        byteValue = 1,
        shortValue = 2,
        longValue = 3L,
        floatValue = 4.5F,
        doubleValue = 6.5D,
        charValue = 'z',
        name = "sample",
        type = String.class,
        status = JandexGizmoStatus.READY,
        nested = @NestedRuntimeMarker("nested"),
        booleans = {},
        bytes = {},
        shorts = {},
        ints = {},
        longs = {},
        floats = {},
        doubles = {},
        chars = {},
        tags = {},
        types = {},
        statuses = {},
        nestedArray = {})
@ClassRetentionMarker(number = 11)
class JandexGizmoSample<T extends Number & Comparable<T>> {
    public static final String CONSTANT = "constant";

    public int count;

    List<@RuntimeMarker(3) String> names;

    @RuntimeMarker(4)
    String visibleName;

    @ClassRetentionMarker(number = 5)
    String invisibleName;

    @FieldOnlyMarker("copy-me")
    String copiedAnnotation;

    JandexGizmoSample(int count, String visibleName) {
        this.count = count;
        this.visibleName = visibleName;
    }

    public Map<String, ? extends Number> transform(List<? super String> input, int[] numbers) {
        return Map.of();
    }

    public <U extends CharSequence> U generic(U value) {
        return value;
    }

    public <E extends IOException> String risky(String value) throws @RuntimeMarker(7) E {
        return value;
    }
}

enum JandexGizmoStatus {
    READY,
    DONE
}

@Target({ ElementType.TYPE, ElementType.TYPE_USE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@interface RuntimeMarker {
    int value();

    boolean enabled() default false;

    byte byteValue() default 0;

    short shortValue() default 0;

    long longValue() default 0L;

    float floatValue() default 0.0F;

    double doubleValue() default 0.0D;

    char charValue() default 'a';

    String name() default "";

    Class<?> type() default Object.class;

    JandexGizmoStatus status() default JandexGizmoStatus.READY;

    NestedRuntimeMarker nested() default @NestedRuntimeMarker("default");

    boolean[] booleans() default {};

    byte[] bytes() default {};

    short[] shorts() default {};

    int[] ints() default {};

    long[] longs() default {};

    float[] floats() default {};

    double[] doubles() default {};

    char[] chars() default {};

    String[] tags() default {};

    Class<?>[] types() default {};

    JandexGizmoStatus[] statuses() default {};

    NestedRuntimeMarker[] nestedArray() default {};
}

@Target({ ElementType.TYPE, ElementType.TYPE_USE, ElementType.FIELD })
@Retention(RetentionPolicy.CLASS)
@interface ClassRetentionMarker {
    int number();
}

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface FieldOnlyMarker {
    String value();
}

@Target({})
@Retention(RetentionPolicy.RUNTIME)
@interface NestedRuntimeMarker {
    String value();
}
