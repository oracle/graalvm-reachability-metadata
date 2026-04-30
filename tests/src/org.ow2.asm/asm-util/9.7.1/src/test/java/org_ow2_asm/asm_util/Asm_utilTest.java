/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm_util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.objectweb.asm.Opcodes.ACC_MANDATED;
import static org.objectweb.asm.Opcodes.ACC_MODULE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ACC_TRANSITIVE;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.ICONST_5;
import static org.objectweb.asm.Opcodes.IF_ICMPLT;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V17;
import static org.objectweb.asm.Opcodes.V9;
import static org.objectweb.asm.util.CheckSignatureAdapter.CLASS_SIGNATURE;
import static org.objectweb.asm.util.CheckSignatureAdapter.METHOD_SIGNATURE;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.CheckAnnotationAdapter;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.CheckSignatureAdapter;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceAnnotationVisitor;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceSignatureVisitor;

public class Asm_utilTest {
    @Test
    void checkClassAdapterVerifiesGeneratedClassAndReportsInvalidVisitOrder() {
        byte[] classBytes = createRunnableClass();
        ClassReader reader = new ClassReader(classBytes);
        StringWriter verificationOutput = new StringWriter();

        assertThatCode(() -> CheckClassAdapter.verify(reader, false, new PrintWriter(verificationOutput)))
                .doesNotThrowAnyException();
        assertThat(verificationOutput.toString()).doesNotContain("AnalyzerException");

        ClassVisitor checker = new CheckClassAdapter(null);
        assertThatThrownBy(() -> checker.visitField(ACC_PUBLIC, "fieldBeforeHeader", "I", null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void traceClassVisitorPrintsReadableTextAndAsmifierSourceForClassContents() {
        byte[] classBytes = createRunnableClass();

        String textified = trace(classBytes, new Textifier(), ClassReader.EXPAND_FRAMES);
        assertThat(textified)
                .contains("sample/Generated")
                .contains("java/lang/Runnable")
                .contains("@Ljava/lang/Deprecated;")
                .contains("GETSTATIC java/lang/System.out")
                .contains("INVOKEVIRTUAL java/io/PrintStream.println")
                .contains("MAXSTACK");

        String asmified = trace(classBytes, new ASMifier(), ClassReader.EXPAND_FRAMES);
        assertThat(asmified)
                .contains("classWriter.visit")
                .contains("sample/Generated")
                .contains("methodVisitor.visitFieldInsn")
                .contains("java/lang/System")
                .contains("methodVisitor.visitJumpInsn");
    }

    @Test
    void traceAndCheckClassVisitorHandleModuleMetadata() {
        byte[] moduleBytes = createModuleInfoClass();

        assertThatCode(() -> new ClassReader(moduleBytes).accept(new CheckClassAdapter(null, false), 0))
                .doesNotThrowAnyException();

        String moduleText = trace(moduleBytes, new Textifier(), 0);
        assertThat(moduleText)
                .contains("module sample.module")
                .contains("requires transitive java.logging")
                .contains("exports sample/api")
                .contains("opens sample/internal")
                .contains("uses sample/spi/Service")
                .contains("provides sample/spi/Service with")
                .contains("sample/impl/ServiceImpl");
    }

    @Test
    void signatureUtilitiesValidateAndRenderGenericClassAndMethodSignatures() {
        TraceSignatureVisitor classTrace = new TraceSignatureVisitor(ACC_PUBLIC);
        SignatureReader classSignature = new SignatureReader(
                "<T:Ljava/lang/Number;:Ljava/lang/Comparable<TT;>;>Ljava/lang/Object;Ljava/lang/Iterable<TT;>;");

        classSignature.accept(new CheckSignatureAdapter(CLASS_SIGNATURE, classTrace));

        assertThat(classTrace.getDeclaration())
                .contains("T")
                .contains("Number")
                .contains("Comparable")
                .contains("Iterable");

        TraceSignatureVisitor methodTrace = new TraceSignatureVisitor(ACC_PUBLIC);
        SignatureReader methodSignature = new SignatureReader(
                "<T:Ljava/lang/Object;>(Ljava/util/List<+TT;>;)"
                        + "Ljava/util/Map<Ljava/lang/String;TT;>;^Ljava/lang/Exception;");

        methodSignature.accept(new CheckSignatureAdapter(METHOD_SIGNATURE, methodTrace));

        assertThat(methodTrace.getDeclaration()).contains("List").contains("T");
        assertThat(methodTrace.getReturnType()).contains("Map").contains("String").contains("T");
        assertThat(methodTrace.getExceptions()).contains("Exception");
    }

    @Test
    void annotationUtilitiesTraceNestedValuesAndRejectCallsAfterEnd() {
        Printer printer = new Textifier();
        AnnotationVisitor annotationVisitor = new CheckAnnotationAdapter(new TraceAnnotationVisitor(printer));

        annotationVisitor.visit("name", "example");
        annotationVisitor.visit("count", 3);
        annotationVisitor.visitEnum("retention", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME");
        AnnotationVisitor nested = annotationVisitor.visitAnnotation("nested", "Ljava/lang/Deprecated;");
        nested.visit("since", "9.7.1");
        nested.visitEnd();
        AnnotationVisitor array = annotationVisitor.visitArray("tags");
        array.visit(null, "bytecode");
        array.visit(null, "visitor");
        array.visitEnd();
        annotationVisitor.visitEnd();

        StringWriter output = new StringWriter();
        printer.print(new PrintWriter(output));
        assertThat(output.toString())
                .contains("name=\"example\"")
                .contains("count=3")
                .contains("Ljava/lang/annotation/RetentionPolicy;.RUNTIME")
                .contains("nested")
                .contains("Ljava/lang/Deprecated;")
                .contains("tags")
                .contains("bytecode")
                .contains("visitor");

        assertThatThrownBy(() -> annotationVisitor.visit("tooLate", "value"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void textifierCanRenderTypeAnnotationsAndDescriptorsDirectly() {
        Textifier textifier = new Textifier();
        textifier.visit(V17, ACC_PUBLIC | ACC_SUPER, "sample/Annotated", null, "java/lang/Object", null);
        Printer typeAnnotation = textifier.visitClassTypeAnnotation(
                TypeReference.newSuperTypeReference(-1).getValue(), null, "Ljavax/annotation/Nonnull;", true);
        typeAnnotation.visit("value", "class-level type annotation");
        typeAnnotation.visitAnnotationEnd();
        textifier.visitClassEnd();

        StringWriter output = new StringWriter();
        textifier.print(new PrintWriter(output));
        assertThat(output.toString())
                .contains("sample/Annotated")
                .contains("Ljavax/annotation/Nonnull;")
                .contains("class-level type annotation");
    }

    private static byte[] createRunnableClass() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classWriter.visit(
                V17,
                ACC_PUBLIC | ACC_SUPER,
                "sample/Generated",
                "<T:Ljava/lang/Object;>Ljava/lang/Object;Ljava/lang/Runnable;",
                "java/lang/Object",
                new String[] {"java/lang/Runnable"});
        classWriter.visitSource("Generated.java", null);
        classWriter.visitNestMember("sample/Generated$Nested");
        classWriter.visitInnerClass("sample/Generated$Nested", "sample/Generated", "Nested", ACC_PUBLIC | ACC_STATIC);

        AnnotationVisitor classAnnotation = classWriter.visitAnnotation("Ljava/lang/Deprecated;", true);
        classAnnotation.visit("since", "test-fixture");
        classAnnotation.visitEnd();

        FieldVisitor fieldVisitor = classWriter.visitField(ACC_PRIVATE, "value", "I", null, null);
        fieldVisitor.visitAnnotation("Ljava/lang/Deprecated;", false).visitEnd();
        fieldVisitor.visitEnd();

        addConstructor(classWriter);
        addRunMethod(classWriter);
        addMaxMethod(classWriter);

        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static void addConstructor(ClassWriter classWriter) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ICONST_5);
        methodVisitor.visitFieldInsn(PUTFIELD, "sample/Generated", "value", "I");
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private static void addRunMethod(ClassWriter classWriter) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "run", "()V", null, null);
        methodVisitor.visitAnnotation("Ljava/lang/Override;", false).visitEnd();
        methodVisitor.visitCode();
        Label start = new Label();
        Label end = new Label();
        methodVisitor.visitLabel(start);
        methodVisitor.visitLineNumber(20, start);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("asm-util");
        methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitLabel(end);
        methodVisitor.visitLocalVariable("this", "Lsample/Generated;", null, start, end, 0);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private static void addMaxMethod(ClassWriter classWriter) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "max", "(II)I", null, null);
        methodVisitor.visitCode();
        Label useSecondArgument = new Label();
        methodVisitor.visitVarInsn(ILOAD, 0);
        methodVisitor.visitVarInsn(ILOAD, 1);
        methodVisitor.visitJumpInsn(IF_ICMPLT, useSecondArgument);
        methodVisitor.visitVarInsn(ILOAD, 0);
        methodVisitor.visitInsn(IRETURN);
        methodVisitor.visitLabel(useSecondArgument);
        methodVisitor.visitVarInsn(ILOAD, 1);
        methodVisitor.visitInsn(IRETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private static byte[] createModuleInfoClass() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(V9, ACC_MODULE, "module-info", null, null, null);
        ModuleVisitor moduleVisitor = classWriter.visitModule("sample.module", 0, "1.0");
        moduleVisitor.visitMainClass("sample/Main");
        moduleVisitor.visitPackage("sample/api");
        moduleVisitor.visitRequire("java.base", ACC_MANDATED, null);
        moduleVisitor.visitRequire("java.logging", ACC_TRANSITIVE, null);
        moduleVisitor.visitExport("sample/api", ACC_SYNTHETIC, "consumer.module");
        moduleVisitor.visitOpen("sample/internal", 0);
        moduleVisitor.visitUse("sample/spi/Service");
        moduleVisitor.visitProvide("sample/spi/Service", "sample/impl/ServiceImpl");
        moduleVisitor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static String trace(byte[] classBytes, Printer printer, int parsingOptions) {
        StringWriter output = new StringWriter();
        PrintWriter printWriter = new PrintWriter(output);
        ClassReader classReader = new ClassReader(classBytes);
        classReader.accept(new TraceClassVisitor(null, printer, printWriter), parsingOptions);
        printWriter.flush();
        return output.toString();
    }
}
