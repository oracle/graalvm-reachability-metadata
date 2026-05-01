/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm_commons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.CodeSizeEvaluator;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.SerialVersionUIDAdder;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.commons.StaticInitMerger;
import org.objectweb.asm.commons.TableSwitchGenerator;
import org.objectweb.asm.commons.TryCatchBlockSorter;

import static org.assertj.core.api.Assertions.assertThat;

public class Asm_commonsTest implements Opcodes {
    @Test
    void generatorAdapterBuildsConstructorAndDenseSwitchMethod() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(V17, ACC_PUBLIC | ACC_FINAL, "sample/GeneratedSwitch", null, "java/lang/Object", null);

        GeneratorAdapter constructor = new GeneratorAdapter(
                classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null), ACC_PUBLIC, "<init>", "()V");
        constructor.visitCode();
        constructor.loadThis();
        constructor.invokeConstructor(Type.getObjectType("java/lang/Object"), new Method("<init>", "()V"));
        constructor.returnValue();
        constructor.endMethod();

        Method classifyMethod = new Method("classify", "(I)Ljava/lang/String;");
        GeneratorAdapter classify = new GeneratorAdapter(
                ACC_PUBLIC | ACC_STATIC, classifyMethod, null, null, classWriter);
        Map<Integer, String> caseResults = new HashMap<>();
        caseResults.put(0, "zero");
        caseResults.put(1, "one");
        caseResults.put(4, "four");

        classify.visitCode();
        classify.loadArg(0);
        classify.tableSwitch(new int[] {0, 1, 4}, new TableSwitchGenerator() {
            @Override
            public void generateCase(int key, Label end) {
                classify.push(caseResults.get(key));
                classify.returnValue();
            }

            @Override
            public void generateDefault() {
                classify.push("other");
                classify.returnValue();
            }
        }, true);
        classify.endMethod();
        classWriter.visitEnd();

        MethodInstructionCollector constructorCollector = collectInstructions(classWriter.toByteArray(), "<init>");
        MethodInstructionCollector classifyCollector = collectInstructions(classWriter.toByteArray(), "classify");

        assertThat(constructorCollector.methodCalls)
                .contains("183 java/lang/Object.<init>()V");
        assertThat(classifyCollector.tableSwitchMin).isEqualTo(0);
        assertThat(classifyCollector.tableSwitchMax).isEqualTo(4);
        assertThat(classifyCollector.tableSwitchLabelCount).isEqualTo(5);
        assertThat(classifyCollector.constants).containsExactlyInAnyOrder("zero", "one", "four", "other");
    }

    @Test
    void classRemapperRenamesTypesDescriptorsSignaturesAnnotationsFieldsAndMethods() {
        byte[] originalClass = createAnnotatedAbstractClass();
        Map<String, String> mappings = new HashMap<>();
        mappings.put("com/acme/Original", "renamed/Target");
        mappings.put("com/acme/Dependency", "renamed/DependencyView");
        mappings.put("com/acme/Marker", "renamed/Marker");
        mappings.put("com/acme/Original.dependency", "mappedDependency");
        mappings.put(
                "com/acme/Original.make(Lcom/acme/Dependency;[Lcom/acme/Original;)Lcom/acme/Dependency;",
                "create");

        ClassWriter remappedWriter = new ClassWriter(0);
        new ClassReader(originalClass).accept(new ClassRemapper(remappedWriter, new SimpleRemapper(mappings)), 0);
        ClassSummary summary = summarizeClass(remappedWriter.toByteArray());

        assertThat(summary.name).isEqualTo("renamed/Target");
        assertThat(summary.signature)
                .isEqualTo("Ljava/lang/Object;Ljava/util/function/Supplier<Lrenamed/DependencyView;>;");
        assertThat(summary.annotations).containsExactly("Lrenamed/Marker;");
        assertThat(summary.fields)
                .containsEntry("mappedDependency", "Lrenamed/DependencyView;");
        assertThat(summary.fieldAnnotations.get("mappedDependency"))
                .containsExactly("Lrenamed/Marker;");
        assertThat(summary.methods)
                .containsEntry("create", "(Lrenamed/DependencyView;[Lrenamed/Target;)Lrenamed/DependencyView;");
        assertThat(summary.methodAnnotations.get("create"))
                .containsExactly("Lrenamed/Marker;");
    }

    @Test
    void serialVersionUidAdderAddsComputedFieldAndKeepsExplicitField() {
        ClassSummary generatedSummary = summarizeClass(addSerialVersionUid(createSerializableClass(false)));
        FieldSummary generatedField = generatedSummary.fieldDetails.get("serialVersionUID");

        assertThat(generatedField).isNotNull();
        assertThat(generatedField.descriptor).isEqualTo("J");
        assertThat(generatedField.access & (ACC_STATIC | ACC_FINAL)).isEqualTo(ACC_STATIC | ACC_FINAL);
        assertThat(generatedField.value).isInstanceOf(Long.class);

        ClassSummary explicitSummary = summarizeClass(addSerialVersionUid(createSerializableClass(true)));
        assertThat(explicitSummary.fieldDetails.get("serialVersionUID").value).isEqualTo(123L);
        assertThat(explicitSummary.fieldNames)
                .filteredOn("serialVersionUID"::equals)
                .hasSize(1);
    }

    @Test
    void codeSizeEvaluatorAccountsForCompactWideAndVariableLengthInstructions() {
        CodeSizeEvaluator evaluator = new CodeSizeEvaluator(null);

        evaluator.visitInsn(NOP);
        evaluator.visitVarInsn(ALOAD, 0);
        evaluator.visitVarInsn(ALOAD, 300);
        evaluator.visitJumpInsn(GOTO, new Label());
        evaluator.visitLdcInsn("constant");

        assertThat(evaluator.getMinSize()).isEqualTo(11);
        assertThat(evaluator.getMaxSize()).isEqualTo(14);
    }

    @Test
    void staticInitMergerCreatesOneClinitThatInvokesRenamedInitializers() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        StaticInitMerger merger = new StaticInitMerger("mergedClinit", classWriter);
        merger.visit(V17, ACC_PUBLIC, "sample/HasStaticInitializers", null, "java/lang/Object", null);
        emitEmptyStaticInitializer(merger);
        emitEmptyStaticInitializer(merger);
        merger.visitEnd();

        ClassSummary summary = summarizeClass(classWriter.toByteArray());
        MethodInstructionCollector clinitCollector = collectInstructions(classWriter.toByteArray(), "<clinit>");

        assertThat(summary.methods).containsKeys("<clinit>", "mergedClinit0", "mergedClinit1");
        assertThat(summary.methodNames).filteredOn("<clinit>"::equals).hasSize(1);
        assertThat(clinitCollector.methodCalls)
                .containsExactly(
                        "184 sample/HasStaticInitializers.mergedClinit0()V",
                        "184 sample/HasStaticInitializers.mergedClinit1()V");
    }

    @Test
    void tryCatchBlockSorterOrdersNestedHandlersByProtectedRangeLength() {
        TryCatchCollector collector = new TryCatchCollector();
        TryCatchBlockSorter sorter = new TryCatchBlockSorter(
                collector, ACC_PUBLIC | ACC_STATIC, "guard", "()V", null, null);
        Label startOuter = new Label();
        Label startInner = new Label();
        Label endInner = new Label();
        Label endOuter = new Label();
        Label handlerOuter = new Label();
        Label handlerInner = new Label();

        sorter.visitCode();
        sorter.visitLabel(startOuter);
        sorter.visitInsn(NOP);
        sorter.visitLabel(startInner);
        sorter.visitInsn(NOP);
        sorter.visitLabel(endInner);
        sorter.visitInsn(NOP);
        sorter.visitLabel(endOuter);
        sorter.visitInsn(RETURN);
        sorter.visitLabel(handlerOuter);
        sorter.visitInsn(ATHROW);
        sorter.visitLabel(handlerInner);
        sorter.visitInsn(ATHROW);
        sorter.visitTryCatchBlock(startOuter, endOuter, handlerOuter, "java/lang/RuntimeException");
        sorter.visitTryCatchBlock(startInner, endInner, handlerInner, "java/lang/IllegalArgumentException");
        sorter.visitMaxs(1, 0);
        sorter.visitEnd();

        assertThat(collector.blocks)
                .extracting(block -> block.type)
                .containsExactly("java/lang/IllegalArgumentException", "java/lang/RuntimeException");
    }

    @Test
    void instructionAdapterAndLocalVariablesSorterHandleTypedStackAndLocalSlots() {
        LocalVariablesSorter sorter = new LocalVariablesSorter(
                ACC_PUBLIC, "(IDLjava/lang/String;)V", new MethodVisitor(ASM9) {});
        assertThat(sorter.newLocal(Type.INT_TYPE)).isEqualTo(5);
        assertThat(sorter.newLocal(Type.LONG_TYPE)).isEqualTo(6);
        assertThat(sorter.newLocal(Type.DOUBLE_TYPE)).isEqualTo(8);
        assertThat(sorter.newLocal(Type.getObjectType("java/lang/String"))).isEqualTo(10);

        AnalyzerAdapter analyzer = new AnalyzerAdapter(
                "sample/AnalyzerOwner", ACC_PUBLIC | ACC_STATIC, "compute", "(I)I", new MethodVisitor(ASM9) {});
        InstructionAdapter instructions = new InstructionAdapter(analyzer);
        instructions.visitCode();

        instructions.iconst(2);
        assertThat(analyzer.stack).containsExactly(INTEGER);
        instructions.load(0, Type.INT_TYPE);
        assertThat(analyzer.stack).containsExactly(INTEGER, INTEGER);
        instructions.add(Type.INT_TYPE);
        assertThat(analyzer.stack).containsExactly(INTEGER);
        instructions.store(1, Type.INT_TYPE);
        assertThat(analyzer.stack).isEmpty();
        assertThat(analyzer.locals).containsExactly(INTEGER, INTEGER);
        instructions.iinc(1, 5);
        instructions.load(1, Type.INT_TYPE);
        assertThat(analyzer.stack).containsExactly(INTEGER);
    }

    @Test
    void adviceAdapterInjectsEntryAndExitAdviceForReturnAndThrowPaths() {
        byte[] advisedClass = addDescribeMethodAdvice(createClassWithDescribeMethod());
        MethodInstructionCollector collector = collectInstructions(advisedClass, "describe");

        assertThat(collector.constants)
                .containsExactly(
                        "advice:enter:describe", "missing", "advice:exit:" + ATHROW, "advice:exit:" + ARETURN);
    }

    private static byte[] createAnnotatedAbstractClass() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(
                V17,
                ACC_PUBLIC | ACC_ABSTRACT,
                "com/acme/Original",
                "Ljava/lang/Object;Ljava/util/function/Supplier<Lcom/acme/Dependency;>;",
                "java/lang/Object",
                new String[] {"java/util/function/Supplier"});
        endAnnotation(classWriter.visitAnnotation("Lcom/acme/Marker;", true));
        FieldVisitor fieldVisitor = classWriter.visitField(
                ACC_PRIVATE, "dependency", "Lcom/acme/Dependency;", null, null);
        endAnnotation(fieldVisitor.visitAnnotation("Lcom/acme/Marker;", true));
        fieldVisitor.visitEnd();
        MethodVisitor methodVisitor = classWriter.visitMethod(
                ACC_PUBLIC | ACC_ABSTRACT,
                "make",
                "(Lcom/acme/Dependency;[Lcom/acme/Original;)Lcom/acme/Dependency;",
                null,
                null);
        endAnnotation(methodVisitor.visitAnnotation("Lcom/acme/Marker;", true));
        methodVisitor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static byte[] createSerializableClass(boolean explicitSerialVersionUid) {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(
                V17,
                ACC_PUBLIC,
                "sample/SerializableBean",
                null,
                "java/lang/Object",
                new String[] {"java/io/Serializable"});
        classWriter.visitField(ACC_PRIVATE, "count", "I", null, null).visitEnd();
        if (explicitSerialVersionUid) {
            classWriter.visitField(
                    ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "serialVersionUID", "J", null, 123L).visitEnd();
        }
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static byte[] addSerialVersionUid(byte[] originalClass) {
        ClassWriter classWriter = new ClassWriter(0);
        new ClassReader(originalClass).accept(new SerialVersionUIDAdder(classWriter), 0);
        return classWriter.toByteArray();
    }

    private static byte[] createClassWithDescribeMethod() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(V17, ACC_PUBLIC, "sample/AdvisedTarget", null, "java/lang/Object", null);
        MethodVisitor methodVisitor = classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                "describe",
                "(Ljava/lang/Object;)Ljava/lang/String;",
                null,
                null);
        Label argumentPresent = new Label();
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitJumpInsn(IFNONNULL, argumentPresent);
        methodVisitor.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitLdcInsn("missing");
        methodVisitor.visitMethodInsn(
                INVOKESPECIAL,
                "java/lang/IllegalArgumentException",
                "<init>",
                "(Ljava/lang/String;)V",
                false);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitLabel(argumentPresent);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static byte[] addDescribeMethodAdvice(byte[] originalClass) {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(originalClass).accept(new ClassVisitor(ASM9, classWriter) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"describe".equals(name)) {
                    return methodVisitor;
                }
                return new AdviceAdapter(ASM9, methodVisitor, access, name, descriptor) {
                    @Override
                    protected void onMethodEnter() {
                        visitLdcInsn("advice:enter:" + getName());
                        pop();
                    }

                    @Override
                    protected void onMethodExit(int opcode) {
                        visitLdcInsn("advice:exit:" + opcode);
                        pop();
                    }
                };
            }
        }, 0);
        return classWriter.toByteArray();
    }

    private static void emitEmptyStaticInitializer(ClassVisitor classVisitor) {
        MethodVisitor methodVisitor = classVisitor.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private static void endAnnotation(AnnotationVisitor annotationVisitor) {
        annotationVisitor.visitEnd();
    }

    private static MethodInstructionCollector collectInstructions(byte[] classBytes, String methodName) {
        MethodInstructionCollector collector = new MethodInstructionCollector();
        new ClassReader(classBytes).accept(new ClassVisitor(ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                if (methodName.equals(name)) {
                    return collector;
                }
                return null;
            }
        }, 0);
        return collector;
    }

    private static ClassSummary summarizeClass(byte[] classBytes) {
        ClassSummary summary = new ClassSummary();
        new ClassReader(classBytes).accept(new ClassVisitor(ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces) {
                summary.name = name;
                summary.signature = signature;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                summary.annotations.add(descriptor);
                return null;
            }

            @Override
            public FieldVisitor visitField(
                    int access, String name, String descriptor, String signature, Object value) {
                summary.fieldNames.add(name);
                summary.fields.put(name, descriptor);
                summary.fieldDetails.put(name, new FieldSummary(access, descriptor, value));
                return new FieldVisitor(ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        summary.fieldAnnotations.computeIfAbsent(name, unused -> new ArrayList<>()).add(descriptor);
                        return null;
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                summary.methodNames.add(name);
                summary.methods.put(name, descriptor);
                return new MethodVisitor(ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        summary.methodAnnotations.computeIfAbsent(name, unused -> new ArrayList<>()).add(descriptor);
                        return null;
                    }
                };
            }
        }, 0);
        return summary;
    }

    private static final class MethodInstructionCollector extends MethodVisitor {
        private final List<String> methodCalls = new ArrayList<>();
        private final List<Object> constants = new ArrayList<>();
        private int tableSwitchMin;
        private int tableSwitchMax;
        private int tableSwitchLabelCount;

        private MethodInstructionCollector() {
            super(ASM9);
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            methodCalls.add(opcode + " " + owner + "." + name + descriptor);
        }

        @Override
        public void visitLdcInsn(Object value) {
            constants.add(value);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label defaultHandler, Label... labels) {
            tableSwitchMin = min;
            tableSwitchMax = max;
            tableSwitchLabelCount = labels.length;
        }
    }

    private static final class ClassSummary {
        private String name;
        private String signature;
        private final List<String> annotations = new ArrayList<>();
        private final List<String> fieldNames = new ArrayList<>();
        private final Map<String, String> fields = new LinkedHashMap<>();
        private final Map<String, FieldSummary> fieldDetails = new LinkedHashMap<>();
        private final Map<String, List<String>> fieldAnnotations = new LinkedHashMap<>();
        private final List<String> methodNames = new ArrayList<>();
        private final Map<String, String> methods = new LinkedHashMap<>();
        private final Map<String, List<String>> methodAnnotations = new LinkedHashMap<>();
    }

    private static final class FieldSummary {
        private final int access;
        private final String descriptor;
        private final Object value;

        private FieldSummary(int access, String descriptor, Object value) {
            this.access = access;
            this.descriptor = descriptor;
            this.value = value;
        }
    }

    private static final class TryCatchCollector extends MethodVisitor {
        private final List<TryCatchSummary> blocks = new ArrayList<>();

        private TryCatchCollector() {
            super(ASM9);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            blocks.add(new TryCatchSummary(start, end, handler, type));
        }
    }

    private static final class TryCatchSummary {
        private final Label start;
        private final Label end;
        private final Label handler;
        private final String type;

        private TryCatchSummary(Label start, Label end, Label handler, String type) {
            this.start = start;
            this.end = end;
            this.handler = handler;
            this.type = type;
        }
    }
}
