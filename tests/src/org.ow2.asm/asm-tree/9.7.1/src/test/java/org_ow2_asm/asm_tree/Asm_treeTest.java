/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm_tree;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ModuleExportNode;
import org.objectweb.asm.tree.ModuleNode;
import org.objectweb.asm.tree.ModuleOpenNode;
import org.objectweb.asm.tree.ModuleProvideNode;
import org.objectweb.asm.tree.ModuleRequireNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.RecordComponentNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class Asm_treeTest {
    @Test
    void buildsWritesAndReadsClassTreeWithMembersAnnotationsAndExceptionHandlers() {
        ClassNode generatedClass = buildTreeBackedClass();
        generatedClass.check(Opcodes.ASM9);

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        generatedClass.accept(classWriter);
        byte[] bytecode = classWriter.toByteArray();

        ClassNode parsedClass = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytecode).accept(parsedClass, 0);

        assertThat(parsedClass.name).isEqualTo("generated/TreeBackedExample");
        assertThat(parsedClass.superName).isEqualTo("java/lang/Object");
        assertThat(parsedClass.sourceFile).isEqualTo("TreeBackedExample.java");
        assertThat(parsedClass.visibleAnnotations).hasSize(1);
        AnnotationNode classAnnotation = parsedClass.visibleAnnotations.get(0);
        assertThat(classAnnotation.desc).isEqualTo("Lgenerated/Marker;");
        assertThat(classAnnotation.values).contains("purpose", "tree-api");

        assertThat(parsedClass.fields).hasSize(1);
        FieldNode field = parsedClass.fields.get(0);
        assertThat(field.name).isEqualTo("NAME");
        assertThat(field.desc).isEqualTo("Ljava/lang/String;");
        assertThat(field.value).isEqualTo("tree");
        assertThat(field.invisibleAnnotations).hasSize(1);

        MethodNode parseMethod = findMethod(parsedClass, "parseOrLength", "(Ljava/lang/String;)I");
        assertThat(parseMethod.tryCatchBlocks).hasSize(1);
        TryCatchBlockNode tryCatchBlock = parseMethod.tryCatchBlocks.get(0);
        assertThat(tryCatchBlock.type).isEqualTo("java/lang/NumberFormatException");
        MethodInsnNode parseIntCall = findMethodCall(parseMethod, "java/lang/Integer", "parseInt");
        assertThat(parseIntCall.desc).isEqualTo("(Ljava/lang/String;)I");
        assertThat(parseMethod.instructions.toArray())
                .anySatisfy(node -> assertThat(node).isInstanceOf(LabelNode.class))
                .anySatisfy(node -> assertThat(node).isInstanceOf(MethodInsnNode.class));

        MethodNode switchMethod = findMethod(parsedClass, "classify", "(I)I");
        assertThat(switchMethod.instructions.toArray())
                .anySatisfy(node -> assertThat(node).isInstanceOf(TableSwitchInsnNode.class));
    }

    @Test
    void preservesRecordComponentMetadataInClassTree() {
        ClassNode recordClass = new ClassNode(Opcodes.ASM9);
        recordClass.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_RECORD,
                "generated/TreeRecord",
                null,
                "java/lang/Record",
                null);
        recordClass.visitSource("TreeRecord.java", null);

        RecordComponentVisitor nameComponent = recordClass.visitRecordComponent("name", "Ljava/lang/String;", null);
        AnnotationVisitor componentAnnotation = nameComponent.visitAnnotation("Lgenerated/Component;", true);
        componentAnnotation.visit("role", "primary");
        componentAnnotation.visitEnd();
        nameComponent.visitAnnotation("Lgenerated/InternalComponent;", false).visitEnd();
        nameComponent.visitEnd();
        recordClass.visitRecordComponent(
                "tags",
                "Ljava/util/List;",
                "Ljava/util/List<Ljava/lang/String;>;").visitEnd();
        recordClass.visitEnd();
        recordClass.check(Opcodes.ASM9);

        ClassWriter classWriter = new ClassWriter(0);
        recordClass.accept(classWriter);
        ClassNode parsedClass = new ClassNode(Opcodes.ASM9);
        new ClassReader(classWriter.toByteArray()).accept(parsedClass, 0);

        assertThat(parsedClass.access & Opcodes.ACC_RECORD).isEqualTo(Opcodes.ACC_RECORD);
        assertThat(parsedClass.superName).isEqualTo("java/lang/Record");
        assertThat(parsedClass.recordComponents).hasSize(2);

        RecordComponentNode parsedNameComponent = parsedClass.recordComponents.get(0);
        assertThat(parsedNameComponent.name).isEqualTo("name");
        assertThat(parsedNameComponent.descriptor).isEqualTo("Ljava/lang/String;");
        assertThat(parsedNameComponent.signature).isNull();
        assertThat(parsedNameComponent.visibleAnnotations).hasSize(1);
        assertThat(parsedNameComponent.visibleAnnotations.get(0).desc).isEqualTo("Lgenerated/Component;");
        assertThat(parsedNameComponent.visibleAnnotations.get(0).values).contains("role", "primary");
        assertThat(parsedNameComponent.invisibleAnnotations).hasSize(1);
        assertThat(parsedNameComponent.invisibleAnnotations.get(0).desc).isEqualTo("Lgenerated/InternalComponent;");

        RecordComponentNode parsedTagsComponent = parsedClass.recordComponents.get(1);
        assertThat(parsedTagsComponent.name).isEqualTo("tags");
        assertThat(parsedTagsComponent.descriptor).isEqualTo("Ljava/util/List;");
        assertThat(parsedTagsComponent.signature).isEqualTo("Ljava/util/List<Ljava/lang/String;>;");
    }

    @Test
    void preservesModuleDescriptorTreeMetadata() {
        ClassNode moduleInfo = new ClassNode(Opcodes.ASM9);
        moduleInfo.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null);

        ModuleVisitor module = moduleInfo.visitModule("generated.module", 0, "feature-test");
        module.visitMainClass("generated/app/Main");
        module.visitPackage("generated/app");
        module.visitPackage("generated/spi");
        module.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
        module.visitRequire("java.logging", Opcodes.ACC_TRANSITIVE, null);
        module.visitExport("generated/api", 0, "consumer.module");
        module.visitOpen("generated/internal", Opcodes.ACC_SYNTHETIC, "friend.module");
        module.visitUse("generated/spi/Service");
        module.visitProvide("generated/spi/Service", "generated/spi/impl/ServiceImpl");
        module.visitEnd();
        moduleInfo.visitEnd();
        moduleInfo.check(Opcodes.ASM9);

        ClassWriter classWriter = new ClassWriter(0);
        moduleInfo.accept(classWriter);
        ClassNode parsedClass = new ClassNode(Opcodes.ASM9);
        new ClassReader(classWriter.toByteArray()).accept(parsedClass, 0);

        assertThat(parsedClass.name).isEqualTo("module-info");
        assertThat(parsedClass.access & Opcodes.ACC_MODULE).isEqualTo(Opcodes.ACC_MODULE);
        assertThat(parsedClass.module).isNotNull();

        ModuleNode parsedModule = parsedClass.module;
        assertThat(parsedModule.name).isEqualTo("generated.module");
        assertThat(parsedModule.version).isEqualTo("feature-test");
        assertThat(parsedModule.mainClass).isEqualTo("generated/app/Main");
        assertThat(parsedModule.packages).containsExactly("generated/app", "generated/spi");

        assertThat(parsedModule.requires).hasSize(2);
        ModuleRequireNode javaBase = parsedModule.requires.get(0);
        assertThat(javaBase.module).isEqualTo("java.base");
        assertThat(javaBase.access).isEqualTo(Opcodes.ACC_MANDATED);
        assertThat(javaBase.version).isNull();
        ModuleRequireNode javaLogging = parsedModule.requires.get(1);
        assertThat(javaLogging.module).isEqualTo("java.logging");
        assertThat(javaLogging.access).isEqualTo(Opcodes.ACC_TRANSITIVE);

        assertThat(parsedModule.exports).hasSize(1);
        ModuleExportNode exportedPackage = parsedModule.exports.get(0);
        assertThat(exportedPackage.packaze).isEqualTo("generated/api");
        assertThat(exportedPackage.modules).containsExactly("consumer.module");

        assertThat(parsedModule.opens).hasSize(1);
        ModuleOpenNode openedPackage = parsedModule.opens.get(0);
        assertThat(openedPackage.packaze).isEqualTo("generated/internal");
        assertThat(openedPackage.access).isEqualTo(Opcodes.ACC_SYNTHETIC);
        assertThat(openedPackage.modules).containsExactly("friend.module");

        assertThat(parsedModule.uses).containsExactly("generated/spi/Service");
        assertThat(parsedModule.provides).hasSize(1);
        ModuleProvideNode providedService = parsedModule.provides.get(0);
        assertThat(providedService.service).isEqualTo("generated/spi/Service");
        assertThat(providedService.providers).containsExactly("generated/spi/impl/ServiceImpl");
    }

    @Test
    void mutatesInstructionListMaintainingOrderLinksIndexesAndCloneMappings() {
        InsnList instructions = new InsnList();
        InsnNode one = new InsnNode(Opcodes.ICONST_1);
        InsnNode two = new InsnNode(Opcodes.ICONST_2);
        InsnNode add = new InsnNode(Opcodes.IADD);
        instructions.add(one);
        instructions.add(two);
        instructions.add(add);

        InsnNode zero = new InsnNode(Opcodes.ICONST_0);
        instructions.insert(zero);
        InsnNode three = new InsnNode(Opcodes.ICONST_3);
        instructions.insertBefore(add, three);
        InsnNode four = new InsnNode(Opcodes.ICONST_4);
        instructions.set(two, four);
        instructions.remove(zero);

        assertThat(instructions.size()).isEqualTo(4);
        assertThat(instructions.toArray()).containsExactly(one, four, three, add);
        assertThat(instructions.getFirst()).isSameAs(one);
        assertThat(instructions.getLast()).isSameAs(add);
        assertThat(instructions.contains(two)).isFalse();
        assertThat(instructions.indexOf(three)).isEqualTo(2);
        assertThat(one.getNext()).isSameAs(four);
        assertThat(add.getPrevious()).isSameAs(three);
        ListIterator<AbstractInsnNode> iteratorFromMiddle = instructions.iterator(1);
        assertThat(iteratorFromMiddle.next()).isSameAs(four);

        LabelNode originalLabel = new LabelNode();
        LabelNode clonedLabel = new LabelNode();
        JumpInsnNode jump = new JumpInsnNode(Opcodes.GOTO, originalLabel);
        Map<LabelNode, LabelNode> labelMappings = new HashMap<>();
        labelMappings.put(originalLabel, clonedLabel);

        JumpInsnNode clonedJump = (JumpInsnNode) jump.clone(labelMappings);

        assertThat(clonedJump).isNotSameAs(jump);
        assertThat(clonedJump.getOpcode()).isEqualTo(Opcodes.GOTO);
        assertThat(clonedJump.label).isSameAs(clonedLabel);

        instructions.clear();
        assertThat(instructions.size()).isZero();
        assertThat(instructions.getFirst()).isNull();
    }

    @Test
    void methodNodeVisitorApiRecordsDiverseInstructionAndDebugNodes() {
        MethodNode method = new MethodNode(
                Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "visited",
                "(I)V",
                null,
                null);
        Label loop = new Label();
        Label done = new Label();
        Label defaultCase = new Label();
        Label zeroCase = new Label();
        Label oneCase = new Label();
        Handle bootstrap = new Handle(
                Opcodes.H_INVOKESTATIC,
                "java/lang/invoke/StringConcatFactory",
                "makeConcatWithConstants",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                        + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                false);

        method.visitParameter("counter", Opcodes.ACC_FINAL);
        AnnotationVisitor methodAnnotation = method.visitAnnotation("Lgenerated/Visited;", true);
        methodAnnotation.visit("name", "visited");
        methodAnnotation.visitEnd();
        method.visitAnnotableParameterCount(1, true);
        method.visitParameterAnnotation(0, "Lgenerated/Parameter;", true).visitEnd();
        method.visitCode();
        method.visitLabel(loop);
        method.visitLineNumber(42, loop);
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitJumpInsn(Opcodes.IFEQ, done);
        method.visitIincInsn(0, -1);
        method.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        method.visitInsnAnnotation(
                TypeReference.newTypeReference(TypeReference.NEW).getValue(),
                null,
                "Lgenerated/Instruction;",
                true).visitEnd();
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        method.visitIntInsn(Opcodes.BIPUSH, 7);
        method.visitInvokeDynamicInsn("makeConcatWithConstants", "(I)Ljava/lang/String;", bootstrap, "value");
        method.visitInsn(Opcodes.POP);
        method.visitMultiANewArrayInsn("[[I", 2);
        method.visitInsn(Opcodes.POP);
        method.visitJumpInsn(Opcodes.GOTO, loop);
        method.visitLabel(done);
        method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitTableSwitchInsn(0, 1, defaultCase, zeroCase, oneCase);
        method.visitLabel(zeroCase);
        method.visitInsn(Opcodes.RETURN);
        method.visitLabel(oneCase);
        method.visitInsn(Opcodes.RETURN);
        method.visitLabel(defaultCase);
        method.visitLookupSwitchInsn(defaultCase, new int[] {3, 5}, new Label[] {zeroCase, oneCase});
        method.visitInsn(Opcodes.RETURN);
        method.visitLocalVariable("counter", "I", null, loop, done, 0);
        method.visitMaxs(4, 1);
        method.visitEnd();

        AbstractInsnNode[] nodes = method.instructions.toArray();

        assertThat(method.parameters).hasSize(1);
        assertThat(method.parameters.get(0).name).isEqualTo("counter");
        assertThat(method.visibleAnnotations).hasSize(1);
        assertThat(method.visibleParameterAnnotations[0]).hasSize(1);
        assertThat(nodes).anySatisfy(node -> assertThat(node).isInstanceOf(LineNumberNode.class));
        assertThat(nodes).anySatisfy(node -> assertThat(node).isInstanceOf(JumpInsnNode.class));
        assertThat(nodes).anySatisfy(node -> assertThat(node).isInstanceOf(IincInsnNode.class));
        assertThat(nodes).anySatisfy(node -> assertThat(node).isInstanceOf(TypeInsnNode.class));
        assertThat(nodes).anySatisfy(node -> assertThat(node).isInstanceOf(IntInsnNode.class));
        assertThat(nodes).anySatisfy(node -> assertThat(node).isInstanceOf(InvokeDynamicInsnNode.class));
        assertThat(nodes).anySatisfy(node -> assertThat(node).isInstanceOf(MultiANewArrayInsnNode.class));
        assertThat(nodes).anySatisfy(node -> assertThat(node).isInstanceOf(FrameNode.class));
        assertThat(nodes).anySatisfy(node -> assertThat(node).isInstanceOf(TableSwitchInsnNode.class));
        assertThat(nodes).anySatisfy(node -> assertThat(node).isInstanceOf(LookupSwitchInsnNode.class));
        assertThat(method.localVariables).hasSize(1);
        assertThat(method.localVariables.get(0).name).isEqualTo("counter");
        TypeInsnNode newStringBuilder = findTypeInsn(method, "java/lang/StringBuilder");
        assertThat(newStringBuilder.visibleTypeAnnotations).hasSize(1);
    }

    private static ClassNode buildTreeBackedClass() {
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        classNode.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                "generated/TreeBackedExample",
                null,
                "java/lang/Object",
                null);
        classNode.visitSource("TreeBackedExample.java", null);
        AnnotationVisitor annotation = classNode.visitAnnotation("Lgenerated/Marker;", true);
        annotation.visit("purpose", "tree-api");
        annotation.visitEnum("policy", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME");
        AnnotationVisitor values = annotation.visitArray("values");
        values.visit(null, "alpha");
        values.visit(null, "beta");
        values.visitEnd();
        annotation.visitEnd();

        FieldVisitor field = classNode.visitField(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "NAME",
                "Ljava/lang/String;",
                null,
                "tree");
        field.visitAnnotation("Lgenerated/FieldMarker;", false).visitEnd();
        field.visitEnd();

        addConstructor(classNode);
        addParseOrLengthMethod(classNode);
        addClassifyMethod(classNode);
        classNode.visitEnd();
        return classNode;
    }

    private static void addConstructor(ClassNode classNode) {
        MethodVisitor constructor = classNode.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    private static void addParseOrLengthMethod(ClassNode classNode) {
        MethodVisitor method = classNode.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "parseOrLength",
                "(Ljava/lang/String;)I",
                null,
                null);
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        method.visitCode();
        method.visitTryCatchBlock(start, end, handler, "java/lang/NumberFormatException");
        method.visitLabel(start);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/Integer",
                "parseInt",
                "(Ljava/lang/String;)I",
                false);
        method.visitLabel(end);
        method.visitInsn(Opcodes.IRETURN);
        method.visitLabel(handler);
        method.visitVarInsn(Opcodes.ASTORE, 1);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void addClassifyMethod(ClassNode classNode) {
        MethodVisitor method = classNode.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "classify",
                "(I)I",
                null,
                null);
        Label zero = new Label();
        Label one = new Label();
        Label defaultCase = new Label();
        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitTableSwitchInsn(0, 1, defaultCase, zero, one);
        method.visitLabel(zero);
        method.visitIntInsn(Opcodes.BIPUSH, 10);
        method.visitInsn(Opcodes.IRETURN);
        method.visitLabel(one);
        method.visitIntInsn(Opcodes.BIPUSH, 20);
        method.visitInsn(Opcodes.IRETURN);
        method.visitLabel(defaultCase);
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IADD);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static MethodNode findMethod(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        throw new AssertionError("Expected method not found: " + name + descriptor);
    }

    private static MethodInsnNode findMethodCall(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode methodInsn
                    && methodInsn.owner.equals(owner)
                    && methodInsn.name.equals(name)) {
                return methodInsn;
            }
        }
        throw new AssertionError("Expected method call not found: " + owner + "." + name);
    }

    private static TypeInsnNode findTypeInsn(MethodNode method, String descriptor) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode typeInsn && typeInsn.desc.equals(descriptor)) {
                return typeInsn;
            }
        }
        throw new AssertionError("Expected type instruction not found: " + descriptor);
    }
}
