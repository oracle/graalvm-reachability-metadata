/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm_util;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.CheckAnnotationAdapter;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.CheckFieldAdapter;
import org.objectweb.asm.util.CheckMethodAdapter;
import org.objectweb.asm.util.CheckSignatureAdapter;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceAnnotationVisitor;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceFieldVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;
import org.objectweb.asm.util.TraceSignatureVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Asm_utilTest {
    @Test
    void textifierTraceClassVisitorRendersClassMembersAnnotationsAndInstructions() {
        String trace = traceClass(new Textifier(), buildExampleClass());

        assertThat(trace)
                .contains("generated/asmutil/TracedExample")
                .contains("TracedExample.java")
                .contains("Lgenerated/asmutil/Marker;")
                .contains("NAME")
                .contains("add")
                .contains("IADD")
                .contains("IRETURN")
                .contains("MAXSTACK")
                .contains("MAXLOCALS");
    }

    @Test
    void asmifierTraceClassVisitorEmitsSourceThatRecreatesVisitedClass() {
        String asmified = traceClass(new ASMifier(), buildExampleClass());

        assertThat(asmified)
                .contains("classWriter.visit")
                .contains("V17")
                .contains("generated/asmutil/TracedExample")
                .contains("fieldVisitor.visitAnnotation")
                .contains("methodVisitor.visitInsn(IADD)")
                .contains("methodVisitor.visitMaxs");
    }

    @Test
    void checkAdaptersAcceptWellFormedClassesAndRejectInvalidVisitorUsage() {
        byte[] validClass = buildExampleClass();
        ClassWriter checkedWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor checkedClass = new CheckClassAdapter(checkedWriter, false);

        new ClassReader(validClass).accept(checkedClass, 0);
        assertThat(checkedWriter.toByteArray()).isNotEmpty();

        CheckClassAdapter classAdapter = new CheckClassAdapter(null, false);
        assertThatThrownBy(() -> classAdapter.visitField(Opcodes.ACC_PUBLIC, "field", "I", null, null))
                .isInstanceOf(IllegalStateException.class);

        CheckMethodAdapter methodAdapter = new CheckMethodAdapter(null);
        methodAdapter.visitCode();
        assertThatThrownBy(() -> methodAdapter.visitVarInsn(Opcodes.ALOAD, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid local variable index");

        CheckFieldAdapter fieldAdapter = new CheckFieldAdapter(null);
        fieldAdapter.visitEnd();
        assertThatThrownBy(() -> fieldAdapter.visitAnnotation("Lgenerated/asmutil/AfterEnd;", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void traceMethodVisitorRendersControlFlowAnnotationsAndDebugInformation() {
        Textifier textifier = new Textifier();
        TraceMethodVisitor method = new TraceMethodVisitor(textifier);
        Label start = new Label();
        Label handler = new Label();
        Label done = new Label();
        Label lowCase = new Label();
        Label highCase = new Label();

        method.visitParameter("value", Opcodes.ACC_FINAL);
        AnnotationVisitor annotation = method.visitAnnotation("Lgenerated/asmutil/MethodMarker;", true);
        annotation.visit("name", "branching");
        annotation.visitEnd();
        method.visitCode();
        method.visitTryCatchBlock(start, done, handler, "java/lang/NumberFormatException");
        method.visitLabel(start);
        method.visitLineNumber(21, start);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
        method.visitLookupSwitchInsn(done, new int[] {0, 10}, new Label[] {lowCase, highCase});
        method.visitLabel(lowCase);
        method.visitMultiANewArrayInsn("[[I", 2);
        method.visitInsn(Opcodes.POP);
        method.visitJumpInsn(Opcodes.GOTO, done);
        method.visitLabel(highCase);
        method.visitIincInsn(1, 2);
        method.visitJumpInsn(Opcodes.GOTO, done);
        method.visitLabel(handler);
        method.visitInsn(Opcodes.ICONST_M1);
        method.visitInsnAnnotation(
                TypeReference.newTypeReference(TypeReference.INSTANCEOF).getValue(),
                TypePath.fromString(""),
                "Lgenerated/asmutil/InstructionMarker;",
                true).visitEnd();
        method.visitLabel(done);
        method.visitLocalVariable("value", "Ljava/lang/String;", null, start, done, 0);
        method.visitMaxs(2, 2);
        method.visitEnd();

        String trace = print(textifier);

        assertThat(trace)
                .contains("parameter")
                .contains("value")
                .contains("TRYCATCHBLOCK")
                .contains("INVOKESTATIC java/lang/Integer.parseInt")
                .contains("LOOKUPSWITCH")
                .contains("MULTIANEWARRAY [[I 2")
                .contains("IINC 1 2")
                .contains("LOCALVARIABLE value")
                .contains("MAXSTACK = 2")
                .contains("MAXLOCALS = 2");
    }

    @Test
    void traceFieldAndAnnotationVisitorsRenderNestedAnnotationValues() {
        Textifier textifier = new Textifier();
        TraceFieldVisitor field = new TraceFieldVisitor(textifier);
        AnnotationVisitor traced = field.visitAnnotation("Lgenerated/asmutil/FieldMarker;", true);
        CheckAnnotationAdapter checkedAnnotation = new CheckAnnotationAdapter(
                new TraceAnnotationVisitor(traced, new Textifier()));

        checkedAnnotation.visit("name", "sample");
        checkedAnnotation.visit("priority", Integer.valueOf(7));
        checkedAnnotation.visitEnum("retention", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME");
        AnnotationVisitor nested = checkedAnnotation.visitAnnotation("nested", "Lgenerated/asmutil/Nested;");
        nested.visit("enabled", Boolean.TRUE);
        nested.visitEnd();
        AnnotationVisitor array = checkedAnnotation.visitArray("tags");
        array.visit(null, "asm");
        array.visit(null, "util");
        array.visitEnd();
        checkedAnnotation.visitEnd();
        field.visitEnd();

        String trace = print(textifier);

        assertThat(trace)
                .contains("Lgenerated/asmutil/FieldMarker;")
                .contains("sample")
                .contains("RUNTIME")
                .contains("nested")
                .contains("tags")
                .contains("asm")
                .contains("util");
        assertThatThrownBy(() -> checkedAnnotation.visit("late", "value"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void signatureVisitorsTraceValidateAndWriteGenericSignatures() {
        String methodSignature = "<T:Ljava/lang/Number;>(Ljava/util/List<+TT;>;[Ljava/lang/String;)"
                + "Ljava/util/Map<TT;Ljava/lang/Integer;>;^Ljava/io/IOException;";
        TraceSignatureVisitor traceVisitor = new TraceSignatureVisitor(Opcodes.ACC_PUBLIC);

        CheckClassAdapter.checkMethodSignature(methodSignature);
        new SignatureReader(methodSignature).accept(new CheckSignatureAdapter(CheckSignatureAdapter.METHOD_SIGNATURE, traceVisitor));

        assertThat(traceVisitor.getDeclaration())
                .contains("<T extends java.lang.Number>")
                .contains("java.util.List<? extends T>")
                .contains("java.lang.String[]");
        assertThat(traceVisitor.getReturnType()).contains("java.util.Map<T, java.lang.Integer>");
        assertThat(traceVisitor.getExceptions()).contains("java.io.IOException");

        SignatureWriter writer = new SignatureWriter();
        CheckSignatureAdapter checkedWriter = new CheckSignatureAdapter(CheckSignatureAdapter.METHOD_SIGNATURE, writer);
        checkedWriter.visitFormalTypeParameter("E");
        SignatureVisitor classBound = checkedWriter.visitClassBound();
        classBound.visitClassType("java/lang/Exception");
        classBound.visitEnd();
        SignatureVisitor parameterType = checkedWriter.visitParameterType();
        parameterType.visitClassType("java/util/List");
        SignatureVisitor typeArgument = parameterType.visitTypeArgument(SignatureVisitor.EXTENDS);
        typeArgument.visitTypeVariable("E");
        parameterType.visitEnd();
        checkedWriter.visitReturnType().visitBaseType('V');

        assertThat(writer.toString()).isEqualTo("<E:Ljava/lang/Exception;>(Ljava/util/List<+TE;>;)V");
        assertThatThrownBy(() -> CheckClassAdapter.checkFieldSignature("Ljava/util/List<;"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] buildExampleClass() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                "generated/asmutil/TracedExample",
                null,
                "java/lang/Object",
                null);
        classWriter.visitSource("TracedExample.java", null);

        AnnotationVisitor classAnnotation = classWriter.visitAnnotation("Lgenerated/asmutil/Marker;", true);
        classAnnotation.visit("value", "text");
        classAnnotation.visitEnd();

        FieldVisitor field = classWriter.visitField(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "NAME",
                "Ljava/lang/String;",
                null,
                "asm-util");
        field.visitAnnotation("Lgenerated/asmutil/Name;", true).visitEnd();
        field.visitEnd();

        MethodVisitor constructor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        MethodVisitor add = classWriter.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "add",
                "(II)I",
                null,
                null);
        add.visitCode();
        add.visitVarInsn(Opcodes.ILOAD, 0);
        add.visitVarInsn(Opcodes.ILOAD, 1);
        add.visitInsn(Opcodes.IADD);
        add.visitInsn(Opcodes.IRETURN);
        add.visitMaxs(0, 0);
        add.visitEnd();

        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static String traceClass(Printer printer, byte[] bytecode) {
        StringWriter output = new StringWriter();
        PrintWriter printWriter = new PrintWriter(output);
        TraceClassVisitor traceClassVisitor = new TraceClassVisitor(null, printer, printWriter);
        new ClassReader(bytecode).accept(traceClassVisitor, 0);
        printWriter.flush();
        return output.toString();
    }

    private static String print(Printer printer) {
        StringWriter output = new StringWriter();
        PrintWriter printWriter = new PrintWriter(output);
        printer.print(printWriter);
        printWriter.flush();
        return output.toString();
    }
}
