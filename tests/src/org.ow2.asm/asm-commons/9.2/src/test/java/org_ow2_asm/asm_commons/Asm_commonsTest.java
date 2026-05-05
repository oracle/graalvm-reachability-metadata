/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm_commons;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.CodeSizeEvaluator;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.ModuleHashesAttribute;
import org.objectweb.asm.commons.ModuleResolutionAttribute;
import org.objectweb.asm.commons.ModuleTargetAttribute;
import org.objectweb.asm.commons.SerialVersionUIDAdder;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.commons.StaticInitMerger;
import org.objectweb.asm.commons.TableSwitchGenerator;
import org.objectweb.asm.commons.TryCatchBlockSorter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.RecordComponentNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class Asm_commonsTest {
    @Test
    void generatorAdapterBuildsMethodsWithLocalsBoxingAndTableSwitches() {
        GeneratedCalculator generatedCalculator = buildCalculatorClass();
        ClassNode generatedClass = readClass(generatedCalculator.bytecode());

        assertThat(generatedClass.name).isEqualTo("generated/Calculator");
        assertThat(generatedClass.methods).extracting(method -> method.name)
                .contains("<init>", "classify", "boxedSum");
        assertThat(generatedCalculator.classifyMinSize()).isPositive();
        assertThat(generatedCalculator.classifyMaxSize()).isGreaterThanOrEqualTo(generatedCalculator.classifyMinSize());

        MethodNode classifyMethod = findMethod(generatedClass, "classify", "(I)Ljava/lang/String;");
        assertThat(classifyMethod.instructions.toArray())
                .anySatisfy(instruction -> assertThat(instruction).isInstanceOf(TableSwitchInsnNode.class));

        MethodNode boxedSumMethod = findMethod(generatedClass, "boxedSum", "(II)Ljava/lang/Integer;");
        assertThat(boxedSumMethod.maxLocals).isGreaterThanOrEqualTo(3);
        assertThat(boxedSumMethod.instructions.toArray())
                .anySatisfy(instruction -> assertThat(instruction)
                        .isInstanceOfSatisfying(MethodInsnNode.class, call -> {
                            assertThat(call.owner).isEqualTo("java/lang/Integer");
                            assertThat(call.name).isEqualTo("valueOf");
                            assertThat(call.desc).isEqualTo("(I)Ljava/lang/Integer;");
                        }));
    }

    @Test
    void classRemapperRenamesClassesMembersDescriptorsAnnotationsAndRecordComponents() {
        byte[] originalBytecode = buildRemappableClass();
        Map<String, String> mapping = new HashMap<>();
        mapping.put("example/Original", "renamed/Target");
        mapping.put("example/Service", "renamed/Service");
        mapping.put("example/Anno", "renamed/Annotation");
        mapping.put("example/Value", "renamed/Value");
        mapping.put("example/Problem", "renamed/Problem");
        mapping.put("example/Original.holder", "renamedHolder");
        mapping.put("example/Original.make(Lexample/Value;)Lexample/Value;", "renamedMake");

        SimpleRemapper remapper = new SimpleRemapper(mapping);
        ClassWriter classWriter = new ClassWriter(0);
        new ClassReader(originalBytecode).accept(new ClassRemapper(classWriter, remapper), 0);
        ClassNode remappedClass = readClass(classWriter.toByteArray());

        assertThat(remappedClass.name).isEqualTo("renamed/Target");
        assertThat(remappedClass.interfaces).containsExactly("renamed/Service");
        AnnotationNode classAnnotation = remappedClass.visibleAnnotations.get(0);
        assertThat(classAnnotation.desc).isEqualTo("Lrenamed/Annotation;");
        assertThat(classAnnotation.values).contains("value", Type.getObjectType("renamed/Value"));

        FieldNode remappedField = remappedClass.fields.get(0);
        assertThat(remappedField.name).isEqualTo("renamedHolder");
        assertThat(remappedField.desc).isEqualTo("Lrenamed/Value;");

        MethodNode remappedMethod = findMethod(remappedClass, "renamedMake", "(Lrenamed/Value;)Lrenamed/Value;");
        assertThat(remappedMethod.exceptions).containsExactly("renamed/Problem");
        assertThat(remappedClass.recordComponents).hasSize(1);
        RecordComponentNode component = remappedClass.recordComponents.get(0);
        assertThat(component.descriptor).isEqualTo("Lrenamed/Value;");
    }

    @Test
    void simpleRemapperMapsMemberAnnotationAndInvokeDynamicNames() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("owner/Type.field", "mappedField");
        mapping.put("owner/Type.call(I)V", "mappedCall");
        mapping.put(".bootstrap(Ljava/lang/String;)V", "mappedBootstrap");
        mapping.put("Lowner/Annotation;.value", "mappedValue");
        mapping.put("owner/Type", "mapped/Type");
        SimpleRemapper remapper = new SimpleRemapper(mapping);

        assertThat(remapper.map("owner/Type")).isEqualTo("mapped/Type");
        assertThat(remapper.mapFieldName("owner/Type", "field", "I")).isEqualTo("mappedField");
        assertThat(remapper.mapMethodName("owner/Type", "call", "(I)V")).isEqualTo("mappedCall");
        assertThat(remapper.mapInvokeDynamicMethodName("bootstrap", "(Ljava/lang/String;)V"))
                .isEqualTo("mappedBootstrap");
        assertThat(remapper.mapAnnotationAttributeName("Lowner/Annotation;", "value")).isEqualTo("mappedValue");
        assertThat(remapper.mapFieldName("owner/Type", "unchanged", "I")).isEqualTo("unchanged");
    }

    @Test
    void adviceAdapterInsertsEntryAndExitBytecodeWithoutLoadingGeneratedClass() {
        byte[] originalBytecode = buildIncrementClass();
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor instrumentingVisitor = new ClassVisitor(Opcodes.ASM9, classWriter) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"increment".equals(name)) {
                    return methodVisitor;
                }
                return new AdviceAdapter(Opcodes.ASM9, methodVisitor, access, name, descriptor) {
                    @Override
                    protected void onMethodEnter() {
                        push(7);
                        pop();
                    }

                    @Override
                    protected void onMethodExit(int opcode) {
                        if (opcode == Opcodes.IRETURN) {
                            push(3);
                            math(GeneratorAdapter.ADD, Type.INT_TYPE);
                        }
                    }
                };
            }
        };

        new ClassReader(originalBytecode).accept(instrumentingVisitor, 0);
        MethodNode instrumentedMethod = findMethod(readClass(classWriter.toByteArray()), "increment", "(I)I");

        assertThat(instrumentedMethod.instructions.toArray())
                .anySatisfy(instruction -> assertThat(instruction)
                        .isInstanceOfSatisfying(InsnNode.class,
                                node -> assertThat(node.getOpcode()).isEqualTo(Opcodes.POP)))
                .anySatisfy(instruction -> assertThat(instruction)
                        .isInstanceOfSatisfying(InsnNode.class,
                                node -> assertThat(node.getOpcode()).isEqualTo(Opcodes.IADD)));
    }

    @Test
    void staticInitMergerCreatesSingleInitializerCallingRenamedInitializers() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor merger = new StaticInitMerger("mergedClinit", classWriter);
        merger.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "generated/WithMergedInitializers", null,
                "java/lang/Object", null);
        writeStaticInitializer(merger, "first", 1);
        writeStaticInitializer(merger, "second", 2);
        merger.visitEnd();

        ClassNode mergedClass = readClass(classWriter.toByteArray());
        assertThat(mergedClass.methods).extracting(method -> method.name)
                .contains("mergedClinit0", "mergedClinit1", "<clinit>");
        MethodNode staticInitializer = findMethod(mergedClass, "<clinit>", "()V");
        assertThat(methodCallNames(staticInitializer)).containsExactly("mergedClinit0", "mergedClinit1");
    }

    @Test
    void instructionAdapterAndJsrInlinerReplaceLegacySubroutines() {
        byte[] legacyBytecode = buildClassWithJsrSubroutine();
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor inliningVisitor = new ClassVisitor(Opcodes.ASM9, classWriter) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("legacyFinally".equals(name)) {
                    return new JSRInlinerAdapter(methodVisitor, access, name, descriptor, signature, exceptions);
                }
                return methodVisitor;
            }
        };

        new ClassReader(legacyBytecode).accept(inliningVisitor, 0);
        MethodNode inlinedMethod = findMethod(readClass(classWriter.toByteArray()), "legacyFinally", "()V");

        assertThat(inlinedMethod.instructions.toArray())
                .noneSatisfy(instruction -> assertThat(instruction)
                        .isInstanceOfSatisfying(JumpInsnNode.class,
                                jump -> assertThat(jump.getOpcode()).isEqualTo(Opcodes.JSR)))
                .noneSatisfy(instruction -> assertThat(instruction.getOpcode()).isEqualTo(Opcodes.RET));
    }

    @Test
    void tryCatchBlockSorterOrdersNestedHandlersByProtectedRegionSize() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "generated/SortedHandlers", null,
                "java/lang/Object", null);
        MethodVisitor methodVisitor = new TryCatchBlockSorter(
                classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "nested", "()V", null, null),
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "nested",
                "()V",
                null,
                null);
        Label outerStart = new Label();
        Label innerStart = new Label();
        Label innerEnd = new Label();
        Label outerEnd = new Label();
        Label outerHandler = new Label();
        Label innerHandler = new Label();
        methodVisitor.visitCode();
        methodVisitor.visitTryCatchBlock(outerStart, outerEnd, outerHandler, "java/lang/RuntimeException");
        methodVisitor.visitTryCatchBlock(innerStart, innerEnd, innerHandler, "java/lang/IllegalArgumentException");
        methodVisitor.visitLabel(outerStart);
        methodVisitor.visitInsn(Opcodes.NOP);
        methodVisitor.visitLabel(innerStart);
        methodVisitor.visitInsn(Opcodes.NOP);
        methodVisitor.visitLabel(innerEnd);
        methodVisitor.visitInsn(Opcodes.NOP);
        methodVisitor.visitLabel(outerEnd);
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitLabel(outerHandler);
        methodVisitor.visitInsn(Opcodes.POP);
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitLabel(innerHandler);
        methodVisitor.visitInsn(Opcodes.POP);
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitMaxs(1, 0);
        methodVisitor.visitEnd();
        classWriter.visitEnd();

        MethodNode sortedMethod = findMethod(readClass(classWriter.toByteArray()), "nested", "()V");

        assertThat(sortedMethod.tryCatchBlocks).extracting(block -> block.type)
                .containsExactly("java/lang/IllegalArgumentException", "java/lang/RuntimeException");
    }

    @Test
    void serialVersionUidAdderAddsStableFieldWhenMissingAndPreservesExistingField() {
        byte[] serializableBytecode = buildSerializableClassWithoutSerialVersionUid();
        ClassNode firstGeneratedClass = addSerialVersionUid(serializableBytecode);
        ClassNode secondGeneratedClass = addSerialVersionUid(serializableBytecode);

        FieldNode serialVersionUid = findField(firstGeneratedClass, "serialVersionUID", "J");
        int expectedAccess = Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
        assertThat(serialVersionUid.access & expectedAccess).isEqualTo(expectedAccess);
        assertThat(serialVersionUid.value).isInstanceOf(Long.class);
        assertThat(serialVersionUid.value).isEqualTo(findField(secondGeneratedClass, "serialVersionUID", "J").value);

        ClassNode classWithExistingField = addSerialVersionUid(buildSerializableClassWithSerialVersionUid());
        assertThat(classWithExistingField.fields)
                .filteredOn(field -> "serialVersionUID".equals(field.name))
                .singleElement()
                .extracting(field -> field.value)
                .isEqualTo(123L);
    }

    @Test
    void analyzerAdapterTracksLocalsAndOperandStackAcrossBytecodeInstructions() {
        MethodNode recordedMethod = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "format",
                "(I)Ljava/lang/String;",
                null,
                null);
        AnalyzerAdapter analyzer = new AnalyzerAdapter(
                "generated/AnalyzerSubject",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "format",
                "(I)Ljava/lang/String;",
                recordedMethod);

        analyzer.visitCode();
        assertThat(analyzer.locals).containsExactly(Opcodes.INTEGER);
        assertThat(analyzer.stack).isEmpty();

        analyzer.visitVarInsn(Opcodes.ILOAD, 0);
        assertThat(analyzer.stack).containsExactly(Opcodes.INTEGER);

        analyzer.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/Integer",
                "valueOf",
                "(I)Ljava/lang/Integer;",
                false);
        assertThat(analyzer.stack).containsExactly("java/lang/Integer");

        analyzer.visitVarInsn(Opcodes.ASTORE, 1);
        assertThat(analyzer.locals).containsExactly(Opcodes.INTEGER, "java/lang/Integer");
        assertThat(analyzer.stack).isEmpty();

        analyzer.visitVarInsn(Opcodes.ALOAD, 1);
        analyzer.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/Integer",
                "toString",
                "()Ljava/lang/String;",
                false);
        assertThat(analyzer.stack).containsExactly("java/lang/String");

        analyzer.visitInsn(Opcodes.ARETURN);
        analyzer.visitMaxs(1, 2);
        analyzer.visitEnd();

        assertThat(recordedMethod.instructions.toArray())
                .anySatisfy(instruction -> assertThat(instruction)
                        .isInstanceOfSatisfying(MethodInsnNode.class,
                                call -> assertThat(call.name).isEqualTo("valueOf")))
                .anySatisfy(instruction -> assertThat(instruction)
                        .isInstanceOfSatisfying(MethodInsnNode.class,
                                call -> assertThat(call.name).isEqualTo("toString")));
    }

    @Test
    void moduleAttributesRoundTripThroughClassReaderAndWriter() {
        byte[] moduleInfoBytecode = buildModuleInfoWithCommonsAttributes();
        List<Attribute> attributes = new ArrayList<>();
        ClassVisitor collectingVisitor = new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visitAttribute(Attribute attribute) {
                attributes.add(attribute);
            }
        };

        new ClassReader(moduleInfoBytecode).accept(
                collectingVisitor,
                new Attribute[] {
                    new ModuleTargetAttribute(),
                    new ModuleResolutionAttribute(),
                    new ModuleHashesAttribute()
                },
                0);

        ModuleTargetAttribute targetAttribute = findAttribute(attributes, ModuleTargetAttribute.class);
        assertThat(targetAttribute.platform).isEqualTo("linux-amd64");
        ModuleResolutionAttribute resolutionAttribute = findAttribute(attributes, ModuleResolutionAttribute.class);
        assertThat(resolutionAttribute.resolution).isEqualTo(
                ModuleResolutionAttribute.RESOLUTION_WARN_DEPRECATED
                        | ModuleResolutionAttribute.RESOLUTION_WARN_INCUBATING);
        ModuleHashesAttribute hashesAttribute = findAttribute(attributes, ModuleHashesAttribute.class);
        assertThat(hashesAttribute.algorithm).isEqualTo("SHA-256");
        assertThat(hashesAttribute.modules).containsExactly("dependency.module");
        assertThat(hashesAttribute.hashes).hasSize(1);
        assertThat(hashesAttribute.hashes.get(0)).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
    }

    private static GeneratedCalculator buildCalculatorClass() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "generated/Calculator", null,
                "java/lang/Object", null);
        writeDefaultConstructor(classWriter);

        CodeSizeEvaluator classifySizeEvaluator = new CodeSizeEvaluator(classWriter.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "classify",
                "(I)Ljava/lang/String;",
                null,
                null));
        GeneratorAdapter classify = new GeneratorAdapter(
                classifySizeEvaluator,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "classify",
                "(I)Ljava/lang/String;");
        classify.visitCode();
        classify.loadArg(0);
        classify.tableSwitch(new int[] {0, 1, 2}, new TableSwitchGenerator() {
            @Override
            public void generateCase(int key, Label end) {
                classify.push(switch (key) {
                    case 0 -> "zero";
                    case 1 -> "one";
                    default -> "two";
                });
                classify.goTo(end);
            }

            @Override
            public void generateDefault() {
                classify.push("many");
            }
        });
        classify.returnValue();
        classify.endMethod();

        Method boxedSumMethod = new Method("boxedSum", Type.getObjectType("java/lang/Integer"),
                new Type[] {Type.INT_TYPE, Type.INT_TYPE});
        GeneratorAdapter boxedSum = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                boxedSumMethod,
                null,
                null,
                classWriter);
        boxedSum.visitCode();
        int sumLocal = boxedSum.newLocal(Type.INT_TYPE);
        boxedSum.loadArg(0);
        boxedSum.loadArg(1);
        boxedSum.math(GeneratorAdapter.ADD, Type.INT_TYPE);
        boxedSum.storeLocal(sumLocal);
        boxedSum.loadLocal(sumLocal);
        boxedSum.valueOf(Type.INT_TYPE);
        boxedSum.returnValue();
        boxedSum.endMethod();

        classWriter.visitEnd();
        return new GeneratedCalculator(
                classWriter.toByteArray(),
                classifySizeEvaluator.getMinSize(),
                classifySizeEvaluator.getMaxSize());
    }

    private static byte[] buildRemappableClass() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_RECORD,
                "example/Original", null, "java/lang/Record", new String[] {"example/Service"});
        AnnotationVisitor annotation = classWriter.visitAnnotation("Lexample/Anno;", true);
        annotation.visit("value", Type.getObjectType("example/Value"));
        annotation.visitEnd();
        classWriter.visitRecordComponent("component", "Lexample/Value;", null).visitEnd();
        classWriter.visitField(Opcodes.ACC_PUBLIC, "holder", "Lexample/Value;", null, null).visitEnd();
        classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE, "make",
                "(Lexample/Value;)Lexample/Value;", null, new String[] {"example/Problem"}).visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static byte[] buildIncrementClass() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "generated/Incrementer", null,
                "java/lang/Object", null);
        writeDefaultConstructor(classWriter);
        GeneratorAdapter increment = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                Method.getMethod("int increment (int)"),
                null,
                null,
                classWriter);
        increment.visitCode();
        increment.loadArg(0);
        increment.push(1);
        increment.math(GeneratorAdapter.ADD, Type.INT_TYPE);
        increment.returnValue();
        increment.endMethod();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static void writeStaticInitializer(ClassVisitor classVisitor, String fieldName, int value) {
        classVisitor.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, fieldName, "I", null, null).visitEnd();
        MethodVisitor methodVisitor = classVisitor.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        InstructionAdapter instructions = new InstructionAdapter(methodVisitor);
        instructions.visitCode();
        instructions.iconst(value);
        instructions.putstatic("generated/WithMergedInitializers", fieldName, "I");
        instructions.areturn(Type.VOID_TYPE);
        instructions.visitMaxs(0, 0);
        instructions.visitEnd();
    }

    private static byte[] buildClassWithJsrSubroutine() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, "generated/LegacySubroutine", null,
                "java/lang/Object", null);
        MethodVisitor methodVisitor = classWriter.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "legacyFinally",
                "()V",
                null,
                null);
        InstructionAdapter instructions = new InstructionAdapter(methodVisitor);
        Label subroutine = new Label();
        Label end = new Label();
        instructions.visitCode();
        instructions.jsr(subroutine);
        instructions.goTo(end);
        instructions.visitLabel(subroutine);
        instructions.store(0, Type.getObjectType("java/lang/Object"));
        instructions.ret(0);
        instructions.visitLabel(end);
        instructions.areturn(Type.VOID_TYPE);
        instructions.visitMaxs(1, 1);
        instructions.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static byte[] buildSerializableClassWithoutSerialVersionUid() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "generated/SerializableHolder", null,
                "java/lang/Object", new String[] {"java/io/Serializable"});
        classWriter.visitField(Opcodes.ACC_PRIVATE, "number", "I", null, null).visitEnd();
        classWriter.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT, "cachedText", "Ljava/lang/String;", null,
                null).visitEnd();
        writeDefaultConstructor(classWriter);
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static byte[] buildSerializableClassWithSerialVersionUid() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "generated/SerializableHolderWithUid", null,
                "java/lang/Object", new String[] {"java/io/Serializable"});
        classWriter.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "serialVersionUID", "J", null, 123L).visitEnd();
        classWriter.visitField(Opcodes.ACC_PRIVATE, "number", "I", null, null).visitEnd();
        writeDefaultConstructor(classWriter);
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static ClassNode addSerialVersionUid(byte[] bytecode) {
        ClassWriter classWriter = new ClassWriter(0);
        new ClassReader(bytecode).accept(new SerialVersionUIDAdder(classWriter), 0);
        return readClass(classWriter.toByteArray());
    }

    private static byte[] buildModuleInfoWithCommonsAttributes() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null);
        ModuleVisitor module = classWriter.visitModule("example.module", 0, null);
        module.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
        module.visitEnd();
        classWriter.visitAttribute(new ModuleTargetAttribute("linux-amd64"));
        classWriter.visitAttribute(new ModuleResolutionAttribute(
                ModuleResolutionAttribute.RESOLUTION_WARN_DEPRECATED
                        | ModuleResolutionAttribute.RESOLUTION_WARN_INCUBATING));
        classWriter.visitAttribute(new ModuleHashesAttribute(
                "SHA-256",
                List.of("dependency.module"),
                List.of(new byte[] {1, 2, 3, 4})));
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static void writeDefaultConstructor(ClassWriter classWriter) {
        GeneratorAdapter constructor = new GeneratorAdapter(
                classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null),
                Opcodes.ACC_PUBLIC,
                "<init>",
                "()V");
        constructor.visitCode();
        constructor.loadThis();
        constructor.invokeConstructor(Type.getObjectType("java/lang/Object"), new Method("<init>", "()V"));
        constructor.returnValue();
        constructor.endMethod();
    }

    private static ClassNode readClass(byte[] bytecode) {
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytecode).accept(classNode, 0);
        return classNode;
    }

    private static MethodNode findMethod(ClassNode classNode, String name, String descriptor) {
        return classNode.methods.stream()
                .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method " + name + descriptor));
    }

    private static FieldNode findField(ClassNode classNode, String name, String descriptor) {
        return classNode.fields.stream()
                .filter(field -> name.equals(field.name) && descriptor.equals(field.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing field " + name + " " + descriptor));
    }

    private static <T extends Attribute> T findAttribute(List<Attribute> attributes, Class<T> attributeType) {
        return attributes.stream()
                .filter(attributeType::isInstance)
                .map(attributeType::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing attribute " + attributeType.getSimpleName()));
    }

    private static List<String> methodCallNames(MethodNode methodNode) {
        List<String> methodNames = new ArrayList<>();
        for (AbstractInsnNode instruction : methodNode.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode methodCall) {
                methodNames.add(methodCall.name);
            }
        }
        return methodNames;
    }

    private record GeneratedCalculator(byte[] bytecode, int classifyMinSize, int classifyMaxSize) {
    }
}
