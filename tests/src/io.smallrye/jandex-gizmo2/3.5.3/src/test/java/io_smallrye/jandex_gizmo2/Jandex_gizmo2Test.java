/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye.jandex_gizmo2;

import static java.lang.constant.ConstantDescs.CD_Boolean;
import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_List;
import static java.lang.constant.ConstantDescs.CD_Map;
import static java.lang.constant.ConstantDescs.CD_Number;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_byte;
import static java.lang.constant.ConstantDescs.CD_char;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_short;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.HashMap;
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
import org.jboss.jandex.gizmo2.Jandex2Gizmo;
import org.jboss.jandex.gizmo2.StringBuilderGen;
import org.junit.jupiter.api.Test;

import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.Var;
import io.quarkus.gizmo2.desc.ClassMethodDesc;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.InterfaceMethodDesc;

public class Jandex_gizmo2Test {
    private static final ClassDesc FOO_BAR_DESC = classDesc(FooBar.class);
    private static final ClassDesc SAMPLE_INTERFACE_DESC = classDesc(SampleInterface.class);
    private static final ClassDesc SAMPLE_ENUM_DESC = classDesc(SampleEnum.class);
    private static final ClassDesc INNER_A_DESC = classDesc(InnerA.class);
    private static final ClassDesc INNER_A_B_DESC = classDesc(InnerA.B.class);

    @Target({ ElementType.TYPE, ElementType.TYPE_USE })
    @Retention(RetentionPolicy.RUNTIME)
    @interface NestedAnnotation {
        String value();
    }

    @Target({ ElementType.TYPE, ElementType.TYPE_USE })
    @Retention(RetentionPolicy.RUNTIME)
    @interface RichAnnotation {
        boolean flag();

        byte byteValue();

        short shortValue();

        int intValue();

        long longValue();

        float floatValue();

        double doubleValue();

        char charValue();

        String text();

        Class<?> type();

        SampleEnum mode();

        NestedAnnotation nested();

        int[] numbers();

        String[] emptyStrings() default {};

        SampleEnum[] emptyModes() default {};

        NestedAnnotation[] emptyNested() default {};

        Class<?>[] types() default {};
    }

    enum SampleEnum {
        FIRST,
        SECOND
    }

    @RichAnnotation(
            flag = true,
            byteValue = 1,
            shortValue = 2,
            intValue = 3,
            longValue = 4,
            floatValue = 5.0F,
            doubleValue = 6.0,
            charValue = 'x',
            text = "sample",
            type = String.class,
            mode = SampleEnum.SECOND,
            nested = @NestedAnnotation("nested"),
            numbers = { 7, 8 },
            emptyStrings = {},
            emptyModes = {},
            emptyNested = {},
            types = { String.class, Integer.class })
    public static class AnnotatedSample {
    }

    @SuppressWarnings("unused")
    static class InnerA<T> {
        class B {
        }
    }

    @SuppressWarnings("unused")
    static class FooBar {
        int f1;
        InnerA<String>.B f2;
        Integer[] f3;

        FooBar(int p1, InnerA<String>.B p2, Integer[] p3) {
        }

        void m1(String p1) {
        }

        InnerA<Integer> m2(InnerA<String>.B p1, List<String> p2) {
            return null;
        }

        <T extends Number> T m3(List<?> p1, Map<? extends T, ? super String> p2) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    public static class GenericSample<T extends Number & Comparable<T>> {
        protected T number;

        public static <X extends CharSequence> String join(List<? super String> first,
                List<? extends Number> second, List<?> third, X value) {
            return value.toString();
        }
    }

    interface SampleInterface {
        String transform(String value);
    }

    static final class ThrowingMethods {
        static <E extends IOException> void checkedExceptions() throws E, FileNotFoundException {
        }
    }

    static final class GenericMethods {
        static <T> void annotatedParameters(
                @NestedAnnotation("primitive") int primitive,
                @NestedAnnotation("string") String string,
                List<@NestedAnnotation("list") String> list,
                String[] @NestedAnnotation("array") [] array,
                List<@NestedAnnotation("wildcard") ? extends @NestedAnnotation("bound") String> wildcard,
                Map<?, ? super @NestedAnnotation("super") String> map,
                InnerA<@NestedAnnotation("owner") String>.@NestedAnnotation("inner") B inner,
                @NestedAnnotation("variable") T variable) {
        }
    }

    @Test
    void classDescFromDotNameHandlesPrimitivesReferencesArraysAndCaching() {
        assertEquals(CD_void, Jandex2Gizmo.classDescOf(DotName.createSimple("void")));
        assertEquals(CD_boolean, Jandex2Gizmo.classDescOf(DotName.createSimple("boolean")));
        assertEquals(CD_byte, Jandex2Gizmo.classDescOf(DotName.createSimple("byte")));
        assertEquals(CD_short, Jandex2Gizmo.classDescOf(DotName.createSimple("short")));
        assertEquals(CD_int, Jandex2Gizmo.classDescOf(DotName.createSimple("int")));
        assertEquals(CD_long, Jandex2Gizmo.classDescOf(DotName.createSimple("long")));
        assertEquals(CD_float, Jandex2Gizmo.classDescOf(DotName.createSimple("float")));
        assertEquals(CD_double, Jandex2Gizmo.classDescOf(DotName.createSimple("double")));
        assertEquals(CD_char, Jandex2Gizmo.classDescOf(DotName.createSimple("char")));

        assertEquals(CD_Boolean, Jandex2Gizmo.classDescOf(DotName.BOOLEAN_CLASS_NAME));
        assertEquals(CD_Integer, Jandex2Gizmo.classDescOf(DotName.INTEGER_CLASS_NAME));
        assertEquals(CD_String, Jandex2Gizmo.classDescOf(DotName.STRING_NAME));
        assertEquals(CD_Object, Jandex2Gizmo.classDescOf(DotName.OBJECT_NAME));

        assertEquals(CD_int.arrayType(2), Jandex2Gizmo.classDescOf(DotName.createSimple("[[I")));
        assertEquals(CD_String.arrayType(), Jandex2Gizmo.classDescOf(DotName.createSimple("[Ljava.lang.String;")));
        assertEquals(CD_Object.arrayType(3), Jandex2Gizmo.classDescOf(DotName.createSimple("[[[Ljava.lang.Object;")));

        assertSame(Jandex2Gizmo.classDescOf(DotName.STRING_NAME),
                Jandex2Gizmo.classDescOf(DotName.createSimple("java.lang.String")));
    }

    @Test
    void classDescFromJandexTypesUsesErasure() {
        assertEquals(CD_void, Jandex2Gizmo.classDescOf(VoidType.VOID));
        assertEquals(CD_boolean, Jandex2Gizmo.classDescOf(PrimitiveType.BOOLEAN));
        assertEquals(CD_int, Jandex2Gizmo.classDescOf(PrimitiveType.INT));
        assertEquals(CD_String, Jandex2Gizmo.classDescOf(ClassType.STRING_TYPE));
        assertEquals(CD_Object, Jandex2Gizmo.classDescOf(ClassType.OBJECT_TYPE));
        assertEquals(CD_int.arrayType(4), Jandex2Gizmo.classDescOf(ArrayType.create(PrimitiveType.INT, 4)));
        assertEquals(CD_List, Jandex2Gizmo.classDescOf(ParameterizedType.builder(List.class)
                .addArgument(String.class)
                .build()));
        assertEquals(CD_List.arrayType(), Jandex2Gizmo.classDescOf(ArrayType.create(
                ParameterizedType.builder(List.class).addArgument(String.class).build(), 1)));
        assertEquals(CD_String, Jandex2Gizmo.classDescOf(TypeVariable.builder("T").addBound(String.class).build()));
        assertEquals(CD_Object, Jandex2Gizmo.classDescOf(TypeVariable.create("T")));
    }

    @Test
    void descriptorsAreCreatedFromIndexedMembers() throws IOException {
        ClassInfo clazz = Index.singleClass(FooBar.class);
        assertNotNull(clazz);
        assertEquals(FOO_BAR_DESC, Jandex2Gizmo.classDescOf(clazz));

        assertEquals(FieldDesc.of(FOO_BAR_DESC, "f1", CD_int), Jandex2Gizmo.fieldDescOf(clazz.field("f1")));
        assertEquals(FieldDesc.of(FOO_BAR_DESC, "f2", INNER_A_B_DESC), Jandex2Gizmo.fieldDescOf(clazz.field("f2")));
        assertEquals(FieldDesc.of(FOO_BAR_DESC, "f3", CD_Integer.arrayType()),
                Jandex2Gizmo.fieldDescOf(clazz.field("f3")));

        assertEquals(ClassMethodDesc.of(FOO_BAR_DESC, "m1", MethodTypeDesc.of(CD_void, CD_String)),
                Jandex2Gizmo.methodDescOf(clazz.firstMethod("m1")));
        assertEquals(ClassMethodDesc.of(FOO_BAR_DESC, "m2", MethodTypeDesc.of(INNER_A_DESC, INNER_A_B_DESC, CD_List)),
                Jandex2Gizmo.methodDescOf(clazz.firstMethod("m2")));
        assertEquals(ClassMethodDesc.of(FOO_BAR_DESC, "m3", MethodTypeDesc.of(CD_Number, CD_List, CD_Map)),
                Jandex2Gizmo.methodDescOf(clazz.firstMethod("m3")));
        assertEquals(ConstructorDesc.of(FOO_BAR_DESC, CD_int, INNER_A_B_DESC, CD_Integer.arrayType()),
                Jandex2Gizmo.constructorDescOf(clazz.constructors().get(0)));
    }

    @Test
    void interfaceAndInvalidExecutableDescriptorsAreHandled() throws IOException {
        ClassInfo iface = Index.singleClass(SampleInterface.class);
        MethodInfo transform = iface.firstMethod("transform");
        assertNotNull(transform);
        assertEquals(InterfaceMethodDesc.of(SAMPLE_INTERFACE_DESC, "transform",
                MethodTypeDesc.of(CD_String, CD_String)), Jandex2Gizmo.methodDescOf(transform));

        ClassInfo clazz = Index.singleClass(FooBar.class);
        MethodInfo constructor = clazz.constructors().get(0);
        MethodInfo method = clazz.firstMethod("m1");
        assertThrows(IllegalArgumentException.class, () -> Jandex2Gizmo.methodDescOf(constructor));
        assertThrows(IllegalArgumentException.class, () -> Jandex2Gizmo.constructorDescOf(method));
    }

    @Test
    void genericTypesPreserveParameterizedWildcardsArraysAndTypeVariables() throws IOException {
        ClassInfo clazz = Index.singleClass(GenericSample.class);
        MethodInfo method = clazz.firstMethod("join");
        assertNotNull(method);

        assertEquals("java.util.List<? super java.lang.String>",
                Jandex2Gizmo.genericTypeOf(method.parameterType(0)).toString());
        assertEquals("java.util.List<? extends java.lang.Number>",
                Jandex2Gizmo.genericTypeOf(method.parameterType(1)).toString());
        assertEquals("java.util.List<?>", Jandex2Gizmo.genericTypeOf(method.parameterType(2)).toString());
        assertEquals("X", Jandex2Gizmo.genericTypeOf(method.parameterType(3)).toString());
        assertEquals("? super java.lang.String", typeArgument(method.parameterType(0), 0));
        assertEquals("? extends java.lang.Number", typeArgument(method.parameterType(1), 0));
        assertEquals("?", typeArgument(method.parameterType(2), 0));

        assertThrows(IllegalArgumentException.class, () -> Jandex2Gizmo.genericTypeOfReference(PrimitiveType.INT));
        assertThrows(IllegalArgumentException.class, () -> Jandex2Gizmo.genericTypeOfArray(ClassType.STRING_TYPE));
        assertThrows(IllegalArgumentException.class, () -> Jandex2Gizmo.typeArgumentOf(PrimitiveType.INT));
    }

    @Test
    void throwsTypesPreserveDeclaredExceptionShapes() throws IOException {
        ClassInfo clazz = Index.singleClass(ThrowingMethods.class);
        MethodInfo method = clazz.firstMethod("checkedExceptions");
        assertNotNull(method);

        List<Type> exceptions = method.exceptions();
        assertEquals(2, exceptions.size());
        assertEquals("E", Jandex2Gizmo.genericTypeOfThrows(exceptions.get(0)).toString());
        assertEquals("java.io.FileNotFoundException",
                Jandex2Gizmo.genericTypeOfThrows(exceptions.get(1)).toString());
    }

    @Test
    void genericTypesCanIncludeTypeUseAnnotations() throws IOException {
        Index index = Index.of(NestedAnnotation.class, GenericMethods.class, InnerA.class, InnerA.B.class);
        ClassInfo clazz = index.getClassByName(GenericMethods.class);
        MethodInfo method = clazz.firstMethod("annotatedParameters");
        assertNotNull(method);

        String annotationName = "@" + NestedAnnotation.class.getName();
        assertEquals(annotationName + "(\"primitive\") int",
                Jandex2Gizmo.genericTypeOf(method.parameterType(0), index).toString());
        assertEquals("java.lang." + annotationName + "(\"string\") String",
                Jandex2Gizmo.genericTypeOf(method.parameterType(1), index).toString());
        assertEquals("java.util.List<java.lang." + annotationName + "(\"list\") String>",
                Jandex2Gizmo.genericTypeOf(method.parameterType(2), index).toString());
        assertEquals("java.lang.String[] " + annotationName + "(\"array\") []",
                Jandex2Gizmo.genericTypeOf(method.parameterType(3), index).toString());
        assertEquals("java.util.List<" + annotationName
                + "(\"wildcard\") ? extends java.lang." + annotationName + "(\"bound\") String>",
                Jandex2Gizmo.genericTypeOf(method.parameterType(4), index).toString());
        assertEquals("java.util.Map<?, ? super java.lang." + annotationName + "(\"super\") String>",
                Jandex2Gizmo.genericTypeOf(method.parameterType(5), index).toString());
        assertEquals(InnerA.class.getName() + "<java.lang." + annotationName + "(\"owner\") String>."
                + annotationName + "(\"inner\") B",
                Jandex2Gizmo.genericTypeOf(method.parameterType(6), index).toString());
        assertEquals(annotationName + "(\"variable\") T",
                Jandex2Gizmo.genericTypeOf(method.parameterType(7), index).toString());
    }

    @Test
    void enumConstantsAreConvertedToGizmoConstants() throws IOException {
        Index index = Index.of(SampleEnum.class, FooBar.class);
        ClassInfo enumClass = index.getClassByName(SampleEnum.class);
        FieldInfo second = enumClass.field("SECOND");
        assertNotNull(second);

        assertEquals(Const.of(Enum.EnumDesc.of(SAMPLE_ENUM_DESC, "SECOND")), Jandex2Gizmo.constOfEnum(second));
        assertThrows(IllegalArgumentException.class,
                () -> Jandex2Gizmo.constOfEnum(index.getClassByName(FooBar.class).field("f1")));
    }

    @Test
    void methodTypeParametersCanBeCopiedToGeneratedMethods() throws IOException {
        ClassInfo sourceClass = Index.singleClass(GenericSample.class);
        MethodInfo sourceMethod = sourceClass.firstMethod("join");
        assertNotNull(sourceMethod);

        Map<String, byte[]> generated = new HashMap<>();
        Gizmo gizmo = Gizmo.create(generated::put).withDebugInfo(false).withParameters(false);
        gizmo.class_("io_smallrye.jandex_gizmo2.GeneratedMethodTypeParameters", creator -> {
            creator.staticMethod("copiedJoin", method -> {
                method.public_();
                Jandex2Gizmo.copyTypeParameters(sourceMethod, method);
                method.returning(Jandex2Gizmo.genericTypeOf(sourceMethod.returnType()));
                method.parameter("first", Jandex2Gizmo.genericTypeOf(sourceMethod.parameterType(0)));
                method.parameter("second", Jandex2Gizmo.genericTypeOf(sourceMethod.parameterType(1)));
                method.parameter("third", Jandex2Gizmo.genericTypeOf(sourceMethod.parameterType(2)));
                method.parameter("value", Jandex2Gizmo.genericTypeOf(sourceMethod.parameterType(3)));
                method.body(block -> block.return_("copied"));
            });
        });

        byte[] classBytes = generated.get("io_smallrye/jandex_gizmo2/GeneratedMethodTypeParameters.class");
        assertThat(classBytes).isNotNull().hasSizeGreaterThan(64);

        ClassInfo generatedClass = Index.singleClass(classBytes);
        MethodInfo copiedJoin = generatedClass.firstMethod("copiedJoin");
        assertNotNull(copiedJoin);
        assertThat(copiedJoin.typeParameters()).hasSize(1);
        TypeVariable copiedTypeParameter = copiedJoin.typeParameters().get(0);
        assertEquals("X", copiedTypeParameter.identifier());
        assertThat(copiedTypeParameter.bounds()).hasSize(1);
        assertEquals(DotName.createSimple(CharSequence.class), copiedTypeParameter.bounds().get(0).name());
        assertEquals("java.util.List<? super java.lang.String>", copiedJoin.parameterType(0).toString());
        assertEquals("java.util.List<? extends java.lang.Number>", copiedJoin.parameterType(1).toString());
        assertEquals("java.util.List<?>", copiedJoin.parameterType(2).toString());
        assertEquals("X", copiedJoin.parameterType(3).asTypeVariable().identifier());
    }

    @Test
    void jandexMetadataCanDriveGizmoGeneration() throws IOException {
        Index index = Index.of(RichAnnotation.class, NestedAnnotation.class, AnnotatedSample.class, SampleEnum.class,
                GenericSample.class);
        ClassInfo annotated = index.getClassByName(AnnotatedSample.class);
        ClassInfo generic = index.getClassByName(GenericSample.class);
        assertNotNull(annotated);
        assertNotNull(generic);

        Map<String, byte[]> generated = new HashMap<>();
        Gizmo gizmo = Gizmo.create(generated::put).withDebugInfo(false).withParameters(false);
        gizmo.class_("io_smallrye.jandex_gizmo2.GeneratedBridge", creator -> {
            Jandex2Gizmo.copyAnnotations(annotated, index).accept(creator);
            Jandex2Gizmo.copyTypeParameters(generic, creator);
            Jandex2Gizmo.copyModifiers(generic, creator);

            FieldInfo field = generic.field("number");
            creator.field("copiedNumber", fieldCreator -> {
                fieldCreator.setType(Jandex2Gizmo.genericTypeOf(field.type()));
                Jandex2Gizmo.copyModifiers(field, fieldCreator);
            });

            creator.staticMethod("message", MethodTypeDesc.of(CD_String), method -> {
                method.public_();
                method.body(block -> {
                    StringBuilderGen builder = StringBuilderGen.ofNew(8, block);
                    builder.append("Jandex").append('-').appendCodePoint('G');
                    builder.setLength(8);
                    block.return_(builder.toString_());
                });
            });
            creator.staticMethod("compare", MethodTypeDesc.of(CD_int), method -> {
                method.public_();
                method.body(block -> {
                    StringBuilderGen left = StringBuilderGen.ofNew(block);
                    left.append("a");
                    Var right = block.localVar("rightBuilder", block.new_(StringBuilder.class, Const.of("b")));
                    block.return_(left.compareTo(right));
                });
            });
        });

        byte[] classBytes = generated.get("io_smallrye/jandex_gizmo2/GeneratedBridge.class");
        assertThat(classBytes).isNotNull().hasSizeGreaterThan(64);
        assertThat(classBytes[0]).isEqualTo((byte) 0xCA);
        assertThat(classBytes[1]).isEqualTo((byte) 0xFE);
        assertThat(classBytes[2]).isEqualTo((byte) 0xBA);
        assertThat(classBytes[3]).isEqualTo((byte) 0xBE);
    }

    private static ClassDesc classDesc(Class<?> clazz) {
        return ClassDesc.of(clazz.getName());
    }

    private static String typeArgument(Type parameterizedType, int index) {
        return Jandex2Gizmo.typeArgumentOf(parameterizedType.asParameterizedType().arguments().get(index)).toString();
    }
}
