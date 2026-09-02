/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.asm.AnnotationVisitor;
import com.sleepycat.asm.Attribute;
import com.sleepycat.asm.ByteVector;
import com.sleepycat.asm.ClassAdapter;
import com.sleepycat.asm.ClassReader;
import com.sleepycat.asm.ClassVisitor;
import com.sleepycat.asm.ClassWriter;
import com.sleepycat.asm.FieldVisitor;
import com.sleepycat.asm.Label;
import com.sleepycat.asm.MethodAdapter;
import com.sleepycat.asm.MethodVisitor;
import com.sleepycat.asm.Type;
import com.sleepycat.asm.signature.SignatureReader;
import com.sleepycat.asm.signature.SignatureWriter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class AsmApiCoverageTest {

    @Test
    void typeAndSignatureUtilitiesDescribeGenericMethods() throws Exception {
        Method method = Sample.class.getDeclaredMethod("sample", String.class, int[].class);
        Type[] arguments = Type.getArgumentTypes(method);
        assertThat(arguments).hasSize(2);
        assertThat(Type.getArgumentTypes("(Ljava/lang/String;[I)V")).hasSize(2);
        assertThat(Type.getReturnType(method).getSort()).isEqualTo(Type.VOID);
        assertThat(Type.getReturnType("(I)Ljava/lang/String;").getClassName())
                .isEqualTo("java.lang.String");
        Type array = Type.getType("[[I");
        assertThat(array.getSort()).isEqualTo(Type.ARRAY);
        assertThat(array.getDimensions()).isEqualTo(2);
        assertThat(array.getElementType()).isEqualTo(Type.INT_TYPE);
        assertThat(array.getInternalName()).isEqualTo("[");
        assertThat(array.getDescriptor()).isEqualTo("[[I");
        assertThat(Type.getInternalName(String.class)).isEqualTo("java/lang/String");
        assertThat(Type.getDescriptor(String.class)).isEqualTo("Ljava/lang/String;");
        assertThat(Type.getMethodDescriptor(method)).isEqualTo("(Ljava/lang/String;[I)V");
        assertThat(Type.getMethodDescriptor(Type.VOID_TYPE, arguments)).contains("Ljava/lang/String;");
        assertThat(Type.INT_TYPE.getSize()).isEqualTo(1);
        assertThat(Type.INT_TYPE.getOpcode(21)).isEqualTo(21);
        assertThat(Type.INT_TYPE).isEqualTo(Type.getType(int.class));
        assertThat(Type.INT_TYPE.hashCode()).isEqualTo(Type.getType(int.class).hashCode());
        assertThat(Type.INT_TYPE.toString()).contains("I");

        SignatureWriter writer = new SignatureWriter();
        writer.visitFormalTypeParameter("T");
        writer.visitClassBound().visitClassType("java/lang/Object");
        writer.visitClassBound().visitEnd();
        writer.visitSuperclass().visitClassType("java/lang/Object");
        writer.visitSuperclass().visitEnd();
        writer.visitParameterType().visitTypeVariable("T");
        writer.visitReturnType().visitArrayType().visitBaseType('I');
        writer.visitExceptionType().visitClassType("java/lang/Exception");
        writer.visitExceptionType().visitEnd();
        writer.visitInterface().visitClassType("java/io/Serializable");
        writer.visitInterface().visitEnd();
        writer.visitTypeArgument();
        writer.visitTypeArgument('+').visitClassType("java/lang/Number");
        writer.visitTypeArgument('+').visitEnd();
        writer.visitInnerClassType("Inner");
        writer.visitEnd();
        String signature = writer.toString();
        assertThat(signature).isNotEmpty();
        SignatureWriter interfaceBoundWriter = new SignatureWriter();
        interfaceBoundWriter.visitFormalTypeParameter("T");
        interfaceBoundWriter.visitClassBound().visitClassType("java/lang/Object");
        interfaceBoundWriter.visitClassBound().visitEnd();
        interfaceBoundWriter.visitInterfaceBound().visitClassType("java/io/Serializable");
        interfaceBoundWriter.visitInterfaceBound().visitEnd();
        assertThat(interfaceBoundWriter.toString()).contains("java/io/Serializable");
        SignatureReader reader = new SignatureReader("<T:Ljava/lang/Object;>Ljava/lang/Object;");
        reader.accept(new SignatureWriter());
        new SignatureReader("Ljava/util/List<Ljava/lang/String;>;").acceptType(new SignatureWriter());
    }

    @Test
    void byteVectorAndClassVisitorsBuildAndReadAClass() throws Exception {
        ByteVector vector = new ByteVector();
        assertThat(vector.putByte(1).putShort(2).putInt(3).putLong(4L)
                .putUTF8("value").putByteArray(new byte[] {5, 6}, 0, 2)).isSameAs(vector);

        ClassWriter writer = new ClassWriter(true);
        writer.visit(49, 1, "berkeleydb/je/Generated", null, "java/lang/Object", null);
        writer.visitSource("Generated.java", "debug");
        writer.visitOuterClass("berkeleydb/je/Owner", "method", "()V");
        AnnotationVisitor annotation = writer.visitAnnotation("Ljava/lang/Deprecated;", true);
        annotation.visitEnd();
        writer.visitAttribute(new TestAttribute("ClassAttribute"));
        writer.visitInnerClass("berkeleydb/je/Generated$Nested", "berkeleydb/je/Generated", "Nested", 1);
        FieldVisitor field = writer.visitField(1, "value", "I", null, 1);
        field.visitAnnotation("Ljava/lang/Deprecated;", true).visitEnd();
        field.visitAttribute(new TestAttribute("FieldAttribute"));
        field.visitEnd();
        MethodVisitor method = writer.visitMethod(1, "run", "()V", null, null);
        method.visitCode();
        method.visitInsn(177);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes).startsWith((byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE);
        assertThat(new TestAttribute("ClassAttribute").isUnknown()).isTrue();
        ClassWriter copiedWriter = new ClassWriter(new ClassReader(bytes), true);
        assertThat(copiedWriter.newConst("constant")).isPositive();
        assertThat(copiedWriter.newMethod("berkeleydb/je/Generated", "run", "()V", false))
                .isPositive();

        ClassReader reader = new ClassReader(bytes);
        assertThat(reader.readInt(0)).isEqualTo(0xCAFEBABE);
        assertThat(reader.getItem(1)).isPositive();
        assertThat(reader.readByte(0)).isEqualTo(0xCA);
        assertThat(reader.readShort(6)).isEqualTo((short) 49);
        assertThat(reader.readLong(0)).isNotZero();
        assertThat(reader.readClass(reader.header, new char[32])).isNotNull();
        reader.accept(new ClassAdapter(writer), false);
        reader.accept(new ClassAdapter(writer), new com.sleepycat.asm.Attribute[0], true);
    }

    @Test
    void adaptersForwardEveryPublicVisitorOperation() {
        ClassWriter classWriter = new ClassWriter(true);
        ClassVisitor classAdapter = new ClassAdapter(classWriter);
        classAdapter.visit(49, 1, "A", null, "java/lang/Object", new String[0]);
        classAdapter.visitSource("A.java", null);
        classAdapter.visitOuterClass("A", null, null);
        AnnotationVisitor annotation = classAdapter.visitAnnotation("Lx;", false);
        if (annotation != null) {
            annotation.visit("name", "value");
            annotation.visitEnum("kind", "Lx;", "ONE");
            annotation.visitAnnotation("nested", "Lx;").visitEnd();
            annotation.visitArray("values").visitEnd();
            annotation.visitEnd();
        }
        classAdapter.visitAttribute(new TestAttribute("ClassAttribute"));
        classAdapter.visitInnerClass("A$B", "A", "B", 1);
        classAdapter.visitField(1, "x", "I", null, null).visitEnd();
        MethodVisitor delegate = classWriter.visitMethod(1, "m", "(I)V", null, null);
        MethodVisitor method = new MethodAdapter(delegate);
        Label start = new Label();
        Label end = new Label();
        method.visitAnnotationDefault();
        method.visitAnnotation("Lx;", true);
        method.visitParameterAnnotation(0, "Lx;", true);
        method.visitAttribute(new TestAttribute("MethodAttribute"));
        method.visitCode();
        method.visitInsn(0);
        method.visitIntInsn(16, 1);
        method.visitVarInsn(21, 1);
        method.visitTypeInsn(187, "java/lang/Object");
        method.visitFieldInsn(178, "A", "x", "I");
        method.visitMethodInsn(182, "A", "m", "()V");
        method.visitJumpInsn(167, end);
        method.visitLabel(start);
        method.visitLdcInsn("constant");
        method.visitIincInsn(1, 1);
        method.visitTableSwitchInsn(0, 1, end, new Label[] {start, end});
        method.visitLookupSwitchInsn(end, new int[] {1}, new Label[] {start});
        method.visitMultiANewArrayInsn("[[I", 2);
        method.visitTryCatchBlock(start, end, end, "java/lang/Exception");
        method.visitLocalVariable("x", "I", null, start, end, 1);
        method.visitLineNumber(1, start);
        method.visitMaxs(2, 2);
        method.visitEnd();
        classAdapter.visitEnd();
        assertThat(classWriter.toByteArray()).isNotEmpty();
    }

    @Test
    void classWriterResizesBranchesAndReadsSwitchOffsets() {
        ClassWriter writer = new ClassWriter(false);
        writer.visit(49, 1, "berkeleydb/je/ResizedBranches", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(1, "run", "()V", null, null);
        Label start = new Label();
        Label shiftedTarget = new Label();
        Label wideTarget = new Label();
        Label switchDefault = new Label();
        Label switchCase = new Label();
        method.visitCode();
        method.visitLabel(start);
        method.visitJumpInsn(167, shiftedTarget);
        method.visitJumpInsn(167, wideTarget);
        for (int i = 0; i < 32761; i++) {
            method.visitInsn(0);
        }
        method.visitLabel(shiftedTarget);
        method.visitTableSwitchInsn(0, 0, switchDefault, new Label[] {switchCase});
        method.visitLabel(switchCase);
        method.visitInsn(177);
        method.visitLabel(switchDefault);
        method.visitLookupSwitchInsn(wideTarget, new int[] {1}, new Label[] {wideTarget});
        method.visitLabel(wideTarget);
        method.visitInsn(177);
        method.visitLocalVariable("value", "I", null, start, wideTarget, 0);
        method.visitLineNumber(1, start);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();

        byte[] bytes = writer.toByteArray();
        assertThat(bytes).startsWith((byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE);
        assertThat(bytes.length).isGreaterThan(32761);
    }

    @Test
    void readersAndWritersHandleAnnotationsConstantsAndWideJumps() {
        ClassWriter writer = new ClassWriter(true);
        writer.visit(49, 1, "berkeleydb/je/Annotated", null, "java/lang/Object", null);
        ClassWriter seed = new ClassWriter(true);
        seed.visit(49, 1, "berkeleydb/je/Seed", null, "java/lang/Object", null);
        seed.visitEnd();
        Attribute unknown = new TestAttribute("ClassAttribute").readAttribute(
                new ClassReader(seed.toByteArray()));
        writer.visitAnnotation("Ljava/lang/Deprecated;", true).visitEnd();
        MethodVisitor method = writer.visitMethod(1, "run", "(I)V", null, null);
        AnnotationVisitor values = method.visitAnnotation("Lberkeleydb/je/Values;", true);
        values.visit("integer", 7);
        values.visit("long", 8L);
        values.visit("float", 1.5f);
        values.visit("double", 2.5d);
        values.visit("byte", Byte.valueOf((byte) 3));
        values.visit("boolean", Boolean.TRUE);
        values.visit("short", Short.valueOf((short) 4));
        values.visit("character", Character.valueOf('x'));
        values.visit("text", "value");
        values.visitEnum("kind", "Lberkeleydb/je/Kind;", "ONE");
        values.visit("type", Type.getType(String.class));
        values.visitAnnotation("nested", "Lberkeleydb/je/Nested;").visitEnd();
        AnnotationVisitor numbers = values.visitArray("numbers");
        numbers.visit(null, 1);
        numbers.visit(null, 2);
        numbers.visitEnd();
        values.visitEnd();
        method.visitParameterAnnotation(0, "Ljava/lang/Deprecated;", true).visitEnd();
        method.visitCode();
        Label far = new Label();
        method.visitJumpInsn(167, far);
        for (int i = 0; i < 40000; i++) {
            method.visitInsn(0);
        }
        method.visitLabel(far);
        method.visitInsn(177);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitAttribute(unknown);
        writer.visitEnd();

        assertThat(writer.newConst(1.25f)).isPositive();
        assertThat(writer.newConst(2.5d)).isPositive();
        assertThat(writer.newConst(3L)).isPositive();
        byte[] bytes = writer.toByteArray();
        assertThat(bytes).isNotEmpty();
        new ClassReader(bytes).accept(new ClassAdapter(new ClassWriter(true)), false);
    }

    private static final class TestAttribute extends com.sleepycat.asm.Attribute {
        TestAttribute(String type) {
            super(type);
        }

        Attribute readAttribute(ClassReader reader) {
            return read(reader, 0, 0, null, -1, null);
        }

        @Override
        protected ByteVector write(ClassWriter writer, byte[] code, int len, int maxStack,
                int maxLocals) {
            return new ByteVector();
        }
    }

    private static final class Sample {
        private void sample(String value, int[] values) {
            if (value == null || values == null) {
                throw new IllegalArgumentException();
            }
        }
    }
}
