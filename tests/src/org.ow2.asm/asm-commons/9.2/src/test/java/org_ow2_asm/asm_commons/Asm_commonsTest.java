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
import org.objectweb.asm.RecordComponentVisitor;
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
import org.objectweb.asm.commons.ModuleRemapper;
import org.objectweb.asm.commons.ModuleResolutionAttribute;
import org.objectweb.asm.commons.ModuleTargetAttribute;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SerialVersionUIDAdder;
import org.objectweb.asm.commons.SignatureRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.commons.StaticInitMerger;
import org.objectweb.asm.commons.TableSwitchGenerator;
import org.objectweb.asm.commons.TryCatchBlockSorter;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.RecordComponentNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_MANDATED;
import static org.objectweb.asm.Opcodes.ACC_MODULE;
import static org.objectweb.asm.Opcodes.ACC_OPEN;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_RECORD;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ARRAYLENGTH;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.IADD;
import static org.objectweb.asm.Opcodes.IALOAD;
import static org.objectweb.asm.Opcodes.IASTORE;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.IMUL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.JSR;
import static org.objectweb.asm.Opcodes.NEWARRAY;
import static org.objectweb.asm.Opcodes.NOP;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.RET;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.TABLESWITCH;
import static org.objectweb.asm.Opcodes.V11;
import static org.objectweb.asm.Opcodes.V16;
import static org.objectweb.asm.Opcodes.V1_5;
import static org.objectweb.asm.Opcodes.V9;

public class Asm_commonsTest {
    @Test
    void generatorAdapterBuildsArithmeticAndSwitchMethods() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(V11, ACC_PUBLIC | ACC_SUPER, "example/GeneratedUtility", null, "java/lang/Object", null);
        writeDefaultConstructor(classWriter);

        GeneratorAdapter add = new GeneratorAdapter(
                ACC_PUBLIC | ACC_STATIC,
                new Method("add", "(II)I"),
                null,
                null,
                classWriter);
        add.loadArgs();
        add.math(GeneratorAdapter.ADD, Type.INT_TYPE);
        add.returnValue();
        add.endMethod();

        GeneratorAdapter select = new GeneratorAdapter(
                ACC_PUBLIC | ACC_STATIC,
                new Method("select", "(I)Ljava/lang/String;"),
                null,
                null,
                classWriter);
        select.loadArg(0);
        select.tableSwitch(new int[] {1, 2, 4}, new TableSwitchGenerator() {
            @Override
            public void generateCase(int key, Label end) {
                select.push("case-" + key);
                select.goTo(end);
            }

            @Override
            public void generateDefault() {
                select.push("default");
            }
        }, true);
        select.returnValue();
        select.endMethod();
        classWriter.visitEnd();

        ClassNode generatedClass = readClass(classWriter.toByteArray());
        MethodNode addMethod = method(generatedClass, "add", "(II)I");
        MethodNode selectMethod = method(generatedClass, "select", "(I)Ljava/lang/String;");

        assertThat(opcodes(addMethod)).containsSubsequence(ILOAD, ILOAD, IADD, IRETURN);
        assertThat(opcodes(selectMethod)).contains(TABLESWITCH, ARETURN);
        assertThat(ldcConstants(selectMethod)).contains("case-1", "case-2", "case-4", "default");
    }

    @Test
    void instructionAdapterEmitsTypedArrayAndArithmeticInstructions() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(V11, ACC_PUBLIC | ACC_SUPER, "example/InstructionSample", null, "java/lang/Object", null);
        writeDefaultConstructor(classWriter);

        Type intArrayType = Type.getType("[I");
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "values", "(I)[I", null, null);
        methodVisitor.visitCode();
        InstructionAdapter instructions = new InstructionAdapter(methodVisitor);
        instructions.iconst(3);
        instructions.newarray(Type.INT_TYPE);
        instructions.store(1, intArrayType);

        instructions.load(1, intArrayType);
        instructions.iconst(0);
        instructions.load(0, Type.INT_TYPE);
        instructions.astore(Type.INT_TYPE);

        instructions.load(1, intArrayType);
        instructions.iconst(1);
        instructions.load(0, Type.INT_TYPE);
        instructions.iconst(2);
        instructions.mul(Type.INT_TYPE);
        instructions.astore(Type.INT_TYPE);

        instructions.load(1, intArrayType);
        instructions.iconst(2);
        instructions.load(1, intArrayType);
        instructions.iconst(0);
        instructions.aload(Type.INT_TYPE);
        instructions.load(1, intArrayType);
        instructions.arraylength();
        instructions.add(Type.INT_TYPE);
        instructions.astore(Type.INT_TYPE);

        instructions.load(1, intArrayType);
        instructions.areturn(intArrayType);
        instructions.visitMaxs(0, 0);
        instructions.visitEnd();
        classWriter.visitEnd();

        MethodNode values = method(readClass(classWriter.toByteArray()), "values", "(I)[I");

        assertThat(opcodes(values)).contains(NEWARRAY, IASTORE, IMUL, ARRAYLENGTH, ARETURN);
        assertThat(opcodes(values)).containsSubsequence(ALOAD, IALOAD);
        assertThat(values.maxStack).isGreaterThanOrEqualTo(4);
        assertThat(values.maxLocals).isEqualTo(2);
    }

    @Test
    void classRemapperRewritesClassMembersAnnotationsRecordsAndGenericSignatures() {
        byte[] originalClass = annotatedRecordLikeClass();
        Map<String, String> mappings = new HashMap<>();
        mappings.put("old/Holder", "newpkg/RenamedHolder");
        mappings.put("old/Dependency", "newpkg/RenamedDependency");
        mappings.put("old/Marker", "newpkg/RenamedMarker");
        mappings.put("old/Holder.dependency", "renamedDependency");
        mappings.put("old/Holder.getDependency()Lold/Dependency;", "renamedGetter");
        SimpleRemapper remapper = new SimpleRemapper(mappings);

        ClassWriter classWriter = new ClassWriter(0);
        new ClassReader(originalClass).accept(new ClassRemapper(classWriter, remapper), 0);
        ClassNode remappedClass = readClass(classWriter.toByteArray());

        assertThat(remappedClass.name).isEqualTo("newpkg/RenamedHolder");
        assertThat(remappedClass.signature).contains("Lnewpkg/RenamedDependency;");
        assertThat(remappedClass.visibleAnnotations).singleElement().extracting(annotation -> annotation.desc)
                .isEqualTo("Lnewpkg/RenamedMarker;");

        FieldNode dependencyField = field(remappedClass, "renamedDependency");
        assertThat(dependencyField.desc).isEqualTo("Lnewpkg/RenamedDependency;");
        RecordComponentNode component = remappedClass.recordComponents.get(0);
        assertThat(component.descriptor).isEqualTo("Lnewpkg/RenamedDependency;");
        assertThat(component.visibleAnnotations).singleElement().extracting(annotation -> annotation.desc)
                .isEqualTo("Lnewpkg/RenamedMarker;");

        MethodNode getter = method(remappedClass, "renamedGetter", "()Lnewpkg/RenamedDependency;");
        assertThat(opcodes(getter)).contains(GETFIELD, ARETURN);

        SignatureWriter signatureWriter = new SignatureWriter();
        new SignatureReader("<T:Lold/Dependency;>(Ljava/util/List<Lold/Dependency;>;)Lold/Holder;")
                .accept(new SignatureRemapper(signatureWriter, remapper));
        assertThat(signatureWriter.toString())
                .contains("Lnewpkg/RenamedDependency;")
                .contains("Lnewpkg/RenamedHolder;");
    }

    @Test
    void adviceAdapterAnalyzerAdapterAndCodeSizeEvaluatorInstrumentMethod() {
        byte[] originalClass = simpleArithmeticClass();
        ClassReader classReader = new ClassReader(originalClass);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        List<int[]> measuredSizes = new ArrayList<>();

        classReader.accept(new ClassVisitor(ASM9, classWriter) {
            private String owner;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                owner = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"sumPositive".equals(name)) {
                    return methodVisitor;
                }
                CodeSizeEvaluator codeSizeEvaluator = new CodeSizeEvaluator(methodVisitor) {
                    @Override
                    public void visitEnd() {
                        measuredSizes.add(new int[] {getMinSize(), getMaxSize()});
                        super.visitEnd();
                    }
                };
                AnalyzerAdapter analyzerAdapter = new AnalyzerAdapter(owner, access, name, descriptor, codeSizeEvaluator);
                return new AdviceAdapter(ASM9, analyzerAdapter, access, name, descriptor) {
                    @Override
                    protected void onMethodEnter() {
                        int local = newLocal(Type.INT_TYPE);
                        push(42);
                        storeLocal(local);
                        loadLocal(local);
                        pop();
                    }

                    @Override
                    protected void onMethodExit(int opcode) {
                        if (opcode != ATHROW) {
                            push("exit");
                            pop();
                        }
                    }
                };
            }
        }, 0);

        ClassNode instrumentedClass = readClass(classWriter.toByteArray());
        MethodNode sumPositive = method(instrumentedClass, "sumPositive", "(II)I");

        assertThat(measuredSizes).singleElement().satisfies(size -> assertThat(size[1]).isGreaterThanOrEqualTo(size[0]));
        assertThat(sumPositive.maxLocals).isGreaterThan(2);
        assertThat(ldcConstants(sumPositive)).contains("exit");
        assertThat(opcodes(sumPositive)).contains(IADD, IRETURN);
    }

    @Test
    void staticInitializerMergerAndSerialVersionUidAdderGenerateSupportMembers() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new StaticInitMerger("mergedClinit", new SerialVersionUIDAdder(classWriter));
        visitor.visit(
                V11,
                ACC_PUBLIC | ACC_SUPER,
                "example/SerializableWithInitializers",
                null,
                "java/lang/Object",
                new String[] {"java/io/Serializable"});
        visitor.visitField(ACC_PRIVATE, "value", "I", null, null).visitEnd();
        writeDefaultConstructor(visitor);
        writeEmptyStaticInitializer(visitor);
        writeEmptyStaticInitializer(visitor);
        visitor.visitEnd();

        ClassNode generatedClass = readClass(classWriter.toByteArray());

        FieldNode serialVersionUid = field(generatedClass, "serialVersionUID");
        assertThat(serialVersionUid.desc).isEqualTo("J");
        assertThat(serialVersionUid.access & (ACC_STATIC | ACC_FINAL))
                .isEqualTo(ACC_STATIC | ACC_FINAL);
        assertThat(generatedClass.methods).extracting(method -> method.name)
                .contains("<clinit>", "mergedClinit0", "mergedClinit1");
        assertThat(methodCalls(method(generatedClass, "<clinit>", "()V")))
                .containsExactly("example/SerializableWithInitializers.mergedClinit0()V",
                        "example/SerializableWithInitializers.mergedClinit1()V");
    }

    @Test
    void tryCatchBlockSorterOrdersHandlersByCoveredInstructionRange() {
        byte[] originalClass = unsortedTryCatchClass();
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(originalClass).accept(new ClassVisitor(ASM9, classWriter) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("guarded".equals(name)) {
                    return new TryCatchBlockSorter(methodVisitor, access, name, descriptor, signature, exceptions);
                }
                return methodVisitor;
            }
        }, 0);

        MethodNode guarded = method(readClass(classWriter.toByteArray()), "guarded", "()V");
        assertThat(guarded.tryCatchBlocks).extracting(block -> block.type)
                .containsExactly("java/lang/IllegalArgumentException", "java/lang/RuntimeException");
    }

    @Test
    void jsrInlinerRewritesSubroutineInstructions() {
        byte[] originalClass = classWithJsrSubroutine();
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(originalClass).accept(new ClassVisitor(ASM9, classWriter) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("withSubroutine".equals(name)) {
                    return new JSRInlinerAdapter(methodVisitor, access, name, descriptor, signature, exceptions);
                }
                return methodVisitor;
            }
        }, 0);

        MethodNode inlined = method(readClass(classWriter.toByteArray()), "withSubroutine", "()V");
        assertThat(opcodes(inlined)).doesNotContain(JSR, RET);
        assertThat(opcodes(inlined)).contains(RETURN);
    }

    @Test
    void moduleRemapperRewritesModuleDirectives() {
        ClassNode remappedClass = readClass(remappedModuleDescriptorClass());

        assertThat(remappedClass.module.name).isEqualTo("target.module");
        assertThat(remappedClass.module.mainClass).isEqualTo("new/main/RenamedMain");
        assertThat(remappedClass.module.packages).containsExactly("new/api");
        assertThat(remappedClass.module.requires).extracting(require -> require.module)
                .containsExactly("java.base", "ally.module");
        assertThat(remappedClass.module.exports).singleElement().satisfies(export -> {
            assertThat(export.packaze).isEqualTo("new/api");
            assertThat(export.modules).containsExactly("ally.module");
        });
        assertThat(remappedClass.module.opens).singleElement().satisfies(open -> {
            assertThat(open.packaze).isEqualTo("new/internal");
            assertThat(open.modules).containsExactly("ally.module");
        });
        assertThat(remappedClass.module.uses).containsExactly("new/service/RenamedService");
        assertThat(remappedClass.module.provides).singleElement().satisfies(provide -> {
            assertThat(provide.service).isEqualTo("new/service/RenamedService");
            assertThat(provide.providers).containsExactly("new/impl/RenamedServiceImpl");
        });
    }

    @Test
    void moduleAttributeImplementationsRoundTripThroughClassReader() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(V9, ACC_MODULE, "module-info", null, null, null);
        ModuleVisitor moduleVisitor = classWriter.visitModule("sample.module", ACC_OPEN, "9.2");
        moduleVisitor.visitRequire("java.base", ACC_MANDATED, null);
        moduleVisitor.visitEnd();
        classWriter.visitAttribute(new ModuleTargetAttribute("linux-amd64"));
        classWriter.visitAttribute(new ModuleResolutionAttribute(ModuleResolutionAttribute.RESOLUTION_WARN_INCUBATING));
        classWriter.visitAttribute(new ModuleHashesAttribute(
                "SHA-256",
                Arrays.asList("dependency.module"),
                Arrays.asList(new byte[] {1, 2, 3, 4})));
        classWriter.visitEnd();

        AtomicReference<ModuleTargetAttribute> target = new AtomicReference<>();
        AtomicReference<ModuleResolutionAttribute> resolution = new AtomicReference<>();
        AtomicReference<ModuleHashesAttribute> hashes = new AtomicReference<>();
        new ClassReader(classWriter.toByteArray()).accept(new ClassVisitor(ASM9) {
            @Override
            public void visitAttribute(Attribute attribute) {
                if (attribute instanceof ModuleTargetAttribute) {
                    target.set((ModuleTargetAttribute) attribute);
                } else if (attribute instanceof ModuleResolutionAttribute) {
                    resolution.set((ModuleResolutionAttribute) attribute);
                } else if (attribute instanceof ModuleHashesAttribute) {
                    hashes.set((ModuleHashesAttribute) attribute);
                }
            }
        }, new Attribute[] {
                new ModuleTargetAttribute(),
                new ModuleResolutionAttribute(),
                new ModuleHashesAttribute()
        }, 0);

        assertThat(target.get().platform).isEqualTo("linux-amd64");
        assertThat(resolution.get().resolution).isEqualTo(ModuleResolutionAttribute.RESOLUTION_WARN_INCUBATING);
        assertThat(hashes.get().algorithm).isEqualTo("SHA-256");
        assertThat(hashes.get().modules).containsExactly("dependency.module");
        assertThat(hashes.get().hashes).singleElement()
                .satisfies(hash -> assertThat(hash).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4));
    }

    private static byte[] annotatedRecordLikeClass() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(
                V16,
                ACC_PUBLIC | ACC_SUPER | ACC_RECORD,
                "old/Holder",
                "<T:Lold/Dependency;>Ljava/lang/Object;",
                "java/lang/Object",
                null);
        AnnotationVisitor classAnnotation = classWriter.visitAnnotation("Lold/Marker;", true);
        classAnnotation.visitEnd();
        RecordComponentVisitor recordComponent = classWriter.visitRecordComponent(
                "dependency",
                "Lold/Dependency;",
                null);
        recordComponent.visitAnnotation("Lold/Marker;", true).visitEnd();
        recordComponent.visitEnd();
        classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "dependency", "Lold/Dependency;", null, null).visitEnd();
        writeDefaultConstructor(classWriter);

        MethodVisitor getter = classWriter.visitMethod(ACC_PUBLIC, "getDependency", "()Lold/Dependency;", null, null);
        getter.visitCode();
        getter.visitVarInsn(ALOAD, 0);
        getter.visitFieldInsn(GETFIELD, "old/Holder", "dependency", "Lold/Dependency;");
        getter.visitInsn(ARETURN);
        getter.visitMaxs(1, 1);
        getter.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static byte[] remappedModuleDescriptorClass() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(V9, ACC_MODULE, "module-info", null, null, null);
        Remapper remapper = moduleDirectiveRemapper();
        ModuleVisitor moduleVisitor = new ModuleRemapper(
                classWriter.visitModule(remapper.mapModuleName("source.module"), ACC_OPEN, null),
                remapper);
        moduleVisitor.visitMainClass("old/main/Main");
        moduleVisitor.visitPackage("old/api");
        moduleVisitor.visitRequire("java.base", ACC_MANDATED, null);
        moduleVisitor.visitRequire("friend.module", 0, null);
        moduleVisitor.visitExport("old/api", 0, "friend.module");
        moduleVisitor.visitOpen("old/internal", 0, "friend.module");
        moduleVisitor.visitUse("old/service/Service");
        moduleVisitor.visitProvide("old/service/Service", "old/impl/ServiceImpl");
        moduleVisitor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static Remapper moduleDirectiveRemapper() {
        Map<String, String> internalNameMappings = new HashMap<>();
        internalNameMappings.put("old/main/Main", "new/main/RenamedMain");
        internalNameMappings.put("old/service/Service", "new/service/RenamedService");
        internalNameMappings.put("old/impl/ServiceImpl", "new/impl/RenamedServiceImpl");

        Map<String, String> packageMappings = new HashMap<>();
        packageMappings.put("old/api", "new/api");
        packageMappings.put("old/internal", "new/internal");

        Map<String, String> moduleMappings = new HashMap<>();
        moduleMappings.put("source.module", "target.module");
        moduleMappings.put("friend.module", "ally.module");

        return new Remapper() {
            @Override
            public String map(String internalName) {
                return internalNameMappings.get(internalName);
            }

            @Override
            public String mapPackageName(String name) {
                return packageMappings.getOrDefault(name, name);
            }

            @Override
            public String mapModuleName(String name) {
                return moduleMappings.getOrDefault(name, name);
            }
        };
    }

    private static byte[] simpleArithmeticClass() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(V11, ACC_PUBLIC | ACC_SUPER, "example/Arithmetic", null, "java/lang/Object", null);
        writeDefaultConstructor(classWriter);
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "sumPositive", "(II)I", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ILOAD, 0);
        methodVisitor.visitVarInsn(ILOAD, 1);
        methodVisitor.visitInsn(IADD);
        methodVisitor.visitInsn(IRETURN);
        methodVisitor.visitMaxs(2, 2);
        methodVisitor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static byte[] unsortedTryCatchClass() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(V11, ACC_PUBLIC | ACC_SUPER, "example/TryCatchSample", null, "java/lang/Object", null);
        writeDefaultConstructor(classWriter);
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "guarded", "()V", null, null);
        Label outerStart = new Label();
        Label innerStart = new Label();
        Label innerEnd = new Label();
        Label outerEnd = new Label();
        Label runtimeHandler = new Label();
        Label illegalArgumentHandler = new Label();
        methodVisitor.visitTryCatchBlock(outerStart, outerEnd, runtimeHandler, "java/lang/RuntimeException");
        methodVisitor.visitTryCatchBlock(innerStart, innerEnd, illegalArgumentHandler, "java/lang/IllegalArgumentException");
        methodVisitor.visitCode();
        methodVisitor.visitLabel(outerStart);
        methodVisitor.visitInsn(NOP);
        methodVisitor.visitLabel(innerStart);
        methodVisitor.visitInsn(NOP);
        methodVisitor.visitLabel(innerEnd);
        methodVisitor.visitInsn(NOP);
        methodVisitor.visitLabel(outerEnd);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitLabel(runtimeHandler);
        methodVisitor.visitInsn(POP);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitLabel(illegalArgumentHandler);
        methodVisitor.visitInsn(POP);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 0);
        methodVisitor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static byte[] classWithJsrSubroutine() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(V1_5, ACC_PUBLIC | ACC_SUPER, "example/JsrSample", null, "java/lang/Object", null);
        writeDefaultConstructor(classWriter);
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "withSubroutine", "()V", null, null);
        Label subroutine = new Label();
        methodVisitor.visitCode();
        methodVisitor.visitJumpInsn(JSR, subroutine);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitLabel(subroutine);
        methodVisitor.visitVarInsn(ASTORE, 0);
        methodVisitor.visitVarInsn(RET, 0);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static void writeDefaultConstructor(ClassVisitor classVisitor) {
        MethodVisitor methodVisitor = classVisitor.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
    }

    private static void writeEmptyStaticInitializer(ClassVisitor classVisitor) {
        MethodVisitor methodVisitor = classVisitor.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private static ClassNode readClass(byte[] classBytes) {
        ClassNode classNode = new ClassNode();
        new ClassReader(classBytes).accept(classNode, 0);
        return classNode;
    }

    private static MethodNode method(ClassNode classNode, String name, String descriptor) {
        return classNode.methods.stream()
                .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method " + name + descriptor));
    }

    private static FieldNode field(ClassNode classNode, String name) {
        return classNode.fields.stream()
                .filter(field -> field.name.equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing field " + name));
    }

    private static List<Integer> opcodes(MethodNode methodNode) {
        List<Integer> opcodes = new ArrayList<>();
        for (AbstractInsnNode instruction : methodNode.instructions) {
            if (instruction.getOpcode() >= 0) {
                opcodes.add(instruction.getOpcode());
            }
        }
        return opcodes;
    }

    private static List<Object> ldcConstants(MethodNode methodNode) {
        List<Object> constants = new ArrayList<>();
        for (AbstractInsnNode instruction : methodNode.instructions) {
            if (instruction.getType() == AbstractInsnNode.LDC_INSN) {
                constants.add(((LdcInsnNode) instruction).cst);
            }
        }
        return constants;
    }

    private static List<String> methodCalls(MethodNode methodNode) {
        List<String> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : methodNode.instructions) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;
                calls.add(methodInsnNode.owner + "." + methodInsnNode.name + methodInsnNode.desc);
            }
        }
        return calls;
    }

}
