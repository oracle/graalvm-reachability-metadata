/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_classfilewriter.jboss_classfilewriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.classfilewriter.AccessFlag;
import org.jboss.classfilewriter.ClassFactory;
import org.jboss.classfilewriter.ClassField;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.ClassMethod;
import org.jboss.classfilewriter.annotations.BooleanAnnotationValue;
import org.jboss.classfilewriter.annotations.ClassAnnotation;
import org.jboss.classfilewriter.annotations.ClassAnnotationValue;
import org.jboss.classfilewriter.annotations.IntAnnotationValue;
import org.jboss.classfilewriter.annotations.StringAnnotationValue;
import org.jboss.classfilewriter.code.BranchEnd;
import org.jboss.classfilewriter.code.CodeAttribute;
import org.jboss.classfilewriter.code.ExceptionHandler;
import org.jboss.classfilewriter.code.LookupSwitchBuilder;
import org.jboss.classfilewriter.util.ByteArrayDataOutputStream;
import org.jboss.classfilewriter.util.DescriptorUtils;
import org.junit.jupiter.api.Test;

public class Jboss_classfilewriterTest {
    private static final ClassFactory UNUSED_CLASS_FACTORY = (loader, name, bytecode, offset, length, protectionDomain) -> {
        throw new AssertionError("define() should not be called by this test");
    };

    public interface IntDescriber {
        String describe(int value);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface TypeLabel {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface FieldOrder {
        int value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ReturnTypeHint {
        Class<?> value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface OptionalFlag {
        boolean value();
    }

    @Test
    void generatesInstanceClassStructureAndBytecode() throws Exception {
        String className = generatedClassName("GeneratedIntDescriber");
        ClassFile classFile = new ClassFile(
                className,
                AccessFlag.of(AccessFlag.SUPER, AccessFlag.PUBLIC),
                Object.class.getName(),
                getClass().getClassLoader(),
                UNUSED_CLASS_FACTORY
        );
        classFile.addInterface(IntDescriber.class.getName());

        ClassField prefixField = classFile.addField(
                AccessFlag.of(AccessFlag.PRIVATE, AccessFlag.FINAL),
                "prefix",
                String.class
        );

        ClassMethod constructor = classFile.addMethod(AccessFlag.PUBLIC, "<init>", "V", "Ljava/lang/String;");
        CodeAttribute constructorCode = constructor.getCodeAttribute();
        constructorCode.aload(0);
        constructorCode.invokespecial(Object.class.getName(), "<init>", "V", new String[0]);
        constructorCode.aload(0);
        constructorCode.aload(1);
        constructorCode.putfield(className, "prefix", String.class);
        constructorCode.returnInstruction();

        ClassMethod describeMethod = classFile.addMethod(AccessFlag.PUBLIC, "describe", "Ljava/lang/String;", "I");
        CodeAttribute describeCode = describeMethod.getCodeAttribute();
        describeCode.newInstruction(StringBuilder.class);
        describeCode.dup();
        describeCode.invokespecial(StringBuilder.class.getName(), "<init>", "V", new String[0]);
        describeCode.aload(0);
        describeCode.getfield(className, "prefix", String.class);
        describeCode.invokevirtual(
                StringBuilder.class.getName(),
                "append",
                "Ljava/lang/StringBuilder;",
                new String[] {"Ljava/lang/String;"}
        );
        describeCode.iload(1);
        describeCode.invokevirtual(
                StringBuilder.class.getName(),
                "append",
                "Ljava/lang/StringBuilder;",
                new String[] {"I"}
        );
        describeCode.invokevirtual(StringBuilder.class.getName(), "toString", "Ljava/lang/String;", new String[0]);
        describeCode.returnInstruction();

        ByteArrayDataOutputStream output = new ByteArrayDataOutputStream();
        classFile.write(output);
        byte[] writtenBytes = output.getBytes();
        byte[] cachedBytes = classFile.toBytecode();
        ParsedClass parsedClass = parseClassFile(cachedBytes);

        assertThat(writtenBytes).isNotEmpty();
        assertThat(cachedBytes).isNotEmpty();
        assertThat(classFile.getDescriptor()).isEqualTo("L" + internalName(className) + ";");
        assertThat(classFile.getInterfaces()).containsExactly(IntDescriber.class.getName());
        assertThat(classFile.getFields())
                .extracting(ClassField::getName, ClassField::getDescriptor)
                .containsExactly(tuple("prefix", "Ljava/lang/String;"));
        assertThat(classFile.getMethods())
                .extracting(ClassMethod::getName, ClassMethod::getDescriptor)
                .containsExactlyInAnyOrder(
                        tuple("<init>", "(Ljava/lang/String;)V"),
                        tuple("describe", "(I)Ljava/lang/String;")
                );
        assertThat(prefixField.getClassFile()).isSameAs(classFile);

        assertThat(parsedClass.thisClassName).isEqualTo(internalName(className));
        assertThat(parsedClass.superClassName).isEqualTo("java/lang/Object");
        assertThat(parsedClass.interfaces).containsExactly(internalName(IntDescriber.class));
        assertThat(parsedClass.fields)
                .extracting(field -> field.name, field -> field.descriptor)
                .containsExactly(tuple("prefix", "Ljava/lang/String;"));

        ParsedMethod parsedConstructor = parsedClass.method("<init>", "(Ljava/lang/String;)V");
        ParsedMethod parsedDescribeMethod = parsedClass.method("describe", "(I)Ljava/lang/String;");

        assertThat(parsedConstructor.exceptionTable).isEmpty();
        assertThat(parsedDescribeMethod.exceptionTable).isEmpty();
        assertThat(containsPattern(parsedConstructor.code, 42, 183, -1, -1, 42, 43, 181, -1, -1, 177)).isTrue();
        assertThat(containsPattern(
                parsedDescribeMethod.code,
                187, -1, -1, 89, 183, -1, -1, 42, 180, -1, -1, 182, -1, -1, 27, 182, -1, -1, 182, -1, -1, 176
        )).isTrue();
    }

    @Test
    void generatesSwitchAndExceptionHandlerBytecode() throws Exception {
        String className = generatedClassName("GeneratedStaticMethods");
        ClassFile classFile = new ClassFile(
                className,
                AccessFlag.of(AccessFlag.SUPER, AccessFlag.PUBLIC),
                Object.class.getName(),
                getClass().getClassLoader(),
                UNUSED_CLASS_FACTORY
        );

        ClassMethod classifyMethod = classFile.addMethod(
                AccessFlag.of(AccessFlag.PUBLIC, AccessFlag.STATIC),
                "classify",
                "I",
                "I"
        );
        CodeAttribute classifyCode = classifyMethod.getCodeAttribute();
        classifyCode.iload(0);
        LookupSwitchBuilder switchBuilder = new LookupSwitchBuilder();
        AtomicReference<BranchEnd> zeroCase = switchBuilder.add(0);
        AtomicReference<BranchEnd> sevenCase = switchBuilder.add(7);
        classifyCode.lookupswitch(switchBuilder);
        classifyCode.branchEnd(switchBuilder.getDefaultBranchEnd().get());
        classifyCode.iconst(-1);
        classifyCode.returnInstruction();
        classifyCode.branchEnd(zeroCase.get());
        classifyCode.iconst(10);
        classifyCode.returnInstruction();
        classifyCode.branchEnd(sevenCase.get());
        classifyCode.bipush((byte) 70);
        classifyCode.returnInstruction();

        ClassMethod safeLengthMethod = classFile.addMethod(
                AccessFlag.of(AccessFlag.PUBLIC, AccessFlag.STATIC),
                "safeLength",
                "I",
                DescriptorUtils.makeDescriptor(Integer[].class)
        );
        CodeAttribute safeLengthCode = safeLengthMethod.getCodeAttribute();
        ExceptionHandler handler = safeLengthCode.exceptionBlockStart(RuntimeException.class.getName());
        safeLengthCode.aload(0);
        safeLengthCode.arraylength();
        safeLengthCode.returnInstruction();
        safeLengthCode.exceptionBlockEnd(handler);
        safeLengthCode.exceptionHandlerStart(handler);
        safeLengthCode.iconst(-1);
        safeLengthCode.returnInstruction();

        ParsedClass parsedClass = parseClassFile(classFile.toBytecode());
        ParsedMethod parsedClassifyMethod = parsedClass.method("classify", "(I)I");
        ParsedMethod parsedSafeLengthMethod = parsedClass.method("safeLength", "([Ljava/lang/Integer;)I");

        assertThat(countOpcode(parsedClassifyMethod.code, 171)).isEqualTo(1);
        assertThat(lookupSwitchKeys(parsedClassifyMethod.code)).containsExactly(0, 7);
        assertThat(countOpcode(parsedClassifyMethod.code, 172)).isEqualTo(3);
        assertThat(containsPattern(parsedClassifyMethod.code, 2, 172)).isTrue();
        assertThat(containsPattern(parsedClassifyMethod.code, 16, 10, 172)).isTrue();
        assertThat(containsPattern(parsedClassifyMethod.code, 16, 70, 172)).isTrue();

        assertThat(parsedSafeLengthMethod.exceptionTable)
                .singleElement()
                .satisfies(entry -> assertThat(entry.catchType).isEqualTo("java/lang/RuntimeException"));
        assertThat(containsPattern(parsedSafeLengthMethod.code, 42, 190, 172, 2, 172)).isTrue();
    }

    @Test
    void writesRuntimeVisibleAnnotationsForClassFieldMethodAndParameter() throws Exception {
        String className = generatedClassName("GeneratedAnnotatedMembers");
        ClassFile classFile = new ClassFile(
                className,
                AccessFlag.of(AccessFlag.SUPER, AccessFlag.PUBLIC),
                Object.class.getName(),
                getClass().getClassLoader(),
                UNUSED_CLASS_FACTORY
        );

        classFile.getRuntimeVisibleAnnotationsAttribute().addAnnotation(new ClassAnnotation(
                classFile.getConstPool(),
                TypeLabel.class.getName(),
                List.of(new StringAnnotationValue(classFile.getConstPool(), "value", "generated"))
        ));

        ClassField field = classFile.addField(AccessFlag.PUBLIC, "order", int.class);
        field.getRuntimeVisibleAnnotationsAttribute().addAnnotation(new ClassAnnotation(
                classFile.getConstPool(),
                FieldOrder.class.getName(),
                List.of(new IntAnnotationValue(classFile.getConstPool(), "value", 7))
        ));

        ClassMethod transformMethod = classFile.addMethod(AccessFlag.PUBLIC, "transform", "Ljava/lang/String;", "Ljava/lang/String;");
        transformMethod.getRuntimeVisibleAnnotationsAttribute().addAnnotation(new ClassAnnotation(
                classFile.getConstPool(),
                ReturnTypeHint.class.getName(),
                List.of(new ClassAnnotationValue(classFile.getConstPool(), "value", String.class))
        ));
        transformMethod.getRuntimeVisibleParameterAnnotationsAttribute().addAnnotation(0, new ClassAnnotation(
                classFile.getConstPool(),
                OptionalFlag.class.getName(),
                List.of(new BooleanAnnotationValue(classFile.getConstPool(), "value", true))
        ));
        CodeAttribute transformCode = transformMethod.getCodeAttribute();
        transformCode.aload(1);
        transformCode.returnInstruction();

        ParsedClass parsedClass = parseClassFile(classFile.toBytecode());
        ParsedField parsedField = parsedClass.field("order", "I");
        ParsedMethod parsedTransformMethod = parsedClass.method("transform", "(Ljava/lang/String;)Ljava/lang/String;");

        assertThat(parsedClass.runtimeVisibleAnnotations)
                .singleElement()
                .satisfies(annotation -> {
                    assertThat(annotation.typeDescriptor).isEqualTo(DescriptorUtils.makeDescriptor(TypeLabel.class));
                    assertThat(annotation.values).containsExactly(Map.entry("value", "generated"));
                });
        assertThat(parsedField.runtimeVisibleAnnotations)
                .singleElement()
                .satisfies(annotation -> {
                    assertThat(annotation.typeDescriptor).isEqualTo(DescriptorUtils.makeDescriptor(FieldOrder.class));
                    assertThat(annotation.values).containsExactly(Map.entry("value", 7));
                });
        assertThat(parsedTransformMethod.runtimeVisibleAnnotations)
                .singleElement()
                .satisfies(annotation -> {
                    assertThat(annotation.typeDescriptor).isEqualTo(DescriptorUtils.makeDescriptor(ReturnTypeHint.class));
                    assertThat(annotation.values)
                            .containsExactly(Map.entry("value", DescriptorUtils.makeDescriptor(String.class)));
                });
        assertThat(parsedTransformMethod.parameterAnnotations)
                .singleElement()
                .satisfies(parameterAnnotations -> assertThat(parameterAnnotations)
                        .singleElement()
                        .satisfies(annotation -> {
                            assertThat(annotation.typeDescriptor).isEqualTo(DescriptorUtils.makeDescriptor(OptionalFlag.class));
                            assertThat(annotation.values).containsExactly(Map.entry("value", true));
                        }));
    }

    @Test
    void writesGenericSignaturesAndCheckedExceptions() throws Exception {
        String className = generatedClassName("GeneratedGenericContract");
        ClassFile classFile = new ClassFile(
                className,
                AccessFlag.of(AccessFlag.SUPER, AccessFlag.PUBLIC),
                Object.class.getName(),
                getClass().getClassLoader(),
                UNUSED_CLASS_FACTORY
        );

        String fieldSignature = "Ljava/util/List<Ljava/lang/String;>;";
        ClassField namesField = classFile.addField(AccessFlag.PRIVATE, "names", List.class, fieldSignature);

        ClassMethod copyMethod = classFile.addMethod(AccessFlag.PUBLIC, "copy", "Ljava/util/List;", "Ljava/util/List;");
        String methodSignature = "(Ljava/util/List<Ljava/lang/Integer;>;)Ljava/util/List<Ljava/lang/String;>;";
        copyMethod.setSignature(methodSignature);
        copyMethod.addCheckedExceptions(IOException.class.getName(), ClassNotFoundException.class.getName());
        CodeAttribute copyCode = copyMethod.getCodeAttribute();
        copyCode.aload(1);
        copyCode.returnInstruction();

        ParsedClass parsedClass = parseClassFile(classFile.toBytecode());
        ParsedField parsedField = parsedClass.field("names", "Ljava/util/List;");
        ParsedMethod parsedCopyMethod = parsedClass.method("copy", "(Ljava/util/List;)Ljava/util/List;");

        assertThat(namesField.getSignature()).isEqualTo(fieldSignature);
        assertThat(copyMethod.getSignature()).isEqualTo(methodSignature);
        assertThat(parsedField.signature).isEqualTo(fieldSignature);
        assertThat(parsedCopyMethod.signature).isEqualTo(methodSignature);
        assertThat(parsedCopyMethod.exceptions)
                .containsExactly(internalName(IOException.class), internalName(ClassNotFoundException.class));
        assertThat(containsPattern(parsedCopyMethod.code, 43, 176)).isTrue();
    }

    private static String generatedClassName(String simpleName) {
        return Jboss_classfilewriterTest.class.getPackageName() + "." + simpleName;
    }

    private static String internalName(String className) {
        return className.replace('.', '/');
    }

    private static String internalName(Class<?> type) {
        return internalName(type.getName());
    }

    private static boolean containsPattern(byte[] code, int... pattern) {
        for (int start = 0; start <= code.length - pattern.length; start++) {
            boolean matches = true;
            for (int index = 0; index < pattern.length; index++) {
                int expected = pattern[index];
                if (expected != -1 && (code[start + index] & 0xFF) != expected) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static int countOpcode(byte[] code, int opcode) {
        int count = 0;
        for (byte current : code) {
            if ((current & 0xFF) == opcode) {
                count++;
            }
        }
        return count;
    }

    private static List<Integer> lookupSwitchKeys(byte[] code) {
        int opcodeIndex = -1;
        for (int index = 0; index < code.length; index++) {
            if ((code[index] & 0xFF) == 171) {
                opcodeIndex = index;
                break;
            }
        }
        assertThat(opcodeIndex).isGreaterThanOrEqualTo(0);

        int cursor = opcodeIndex + 1;
        while ((cursor & 3) != 0) {
            cursor++;
        }
        cursor += 4;
        int npairs = readInt(code, cursor);
        cursor += 4;

        List<Integer> keys = new ArrayList<>();
        for (int pair = 0; pair < npairs; pair++) {
            keys.add(readInt(code, cursor));
            cursor += 8;
        }
        return keys;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static ParsedClass parseClassFile(byte[] bytecode) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytecode))) {
            assertThat(input.readInt()).isEqualTo(0xCAFEBABE);
            input.readUnsignedShort();
            input.readUnsignedShort();

            int constantPoolCount = input.readUnsignedShort();
            String[] utf8Entries = new String[constantPoolCount];
            int[] classNameIndexes = new int[constantPoolCount];
            int[] integerEntries = new int[constantPoolCount];
            boolean[] hasIntegerEntries = new boolean[constantPoolCount];
            for (int index = 1; index < constantPoolCount;) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8Entries[index] = input.readUTF();
                    case 3 -> {
                        integerEntries[index] = input.readInt();
                        hasIntegerEntries[index] = true;
                    }
                    case 4 -> input.skipBytes(4);
                    case 5, 6 -> input.skipBytes(8);
                    case 7, 16, 19, 20 -> classNameIndexes[index] = input.readUnsignedShort();
                    case 8 -> input.readUnsignedShort();
                    case 9, 10, 11, 12, 17, 18 -> input.skipBytes(4);
                    case 15 -> input.skipBytes(3);
                    default -> throw new IllegalArgumentException("Unsupported constant-pool tag: " + tag);
                }
                index += tag == 5 || tag == 6 ? 2 : 1;
            }

            input.readUnsignedShort();
            String thisClassName = utf8Entries[classNameIndexes[input.readUnsignedShort()]];
            int superClassIndex = input.readUnsignedShort();
            String superClassName = superClassIndex == 0 ? null : utf8Entries[classNameIndexes[superClassIndex]];

            int interfaceCount = input.readUnsignedShort();
            List<String> interfaces = new ArrayList<>(interfaceCount);
            for (int index = 0; index < interfaceCount; index++) {
                interfaces.add(utf8Entries[classNameIndexes[input.readUnsignedShort()]]);
            }

            int fieldCount = input.readUnsignedShort();
            List<ParsedField> fields = new ArrayList<>(fieldCount);
            for (int index = 0; index < fieldCount; index++) {
                input.readUnsignedShort();
                String name = utf8Entries[input.readUnsignedShort()];
                String descriptor = utf8Entries[input.readUnsignedShort()];
                int attributeCount = input.readUnsignedShort();
                List<ParsedAnnotation> runtimeVisibleAnnotations = List.of();
                String signature = null;
                for (int attributeIndex = 0; attributeIndex < attributeCount; attributeIndex++) {
                    String attributeName = utf8Entries[input.readUnsignedShort()];
                    int attributeLength = input.readInt();
                    if ("RuntimeVisibleAnnotations".equals(attributeName)) {
                        runtimeVisibleAnnotations = readAnnotations(input, utf8Entries, integerEntries, hasIntegerEntries);
                    } else if ("Signature".equals(attributeName)) {
                        signature = utf8Entries[input.readUnsignedShort()];
                    } else {
                        input.skipBytes(attributeLength);
                    }
                }
                fields.add(new ParsedField(name, descriptor, signature, runtimeVisibleAnnotations));
            }

            int methodCount = input.readUnsignedShort();
            List<ParsedMethod> methods = new ArrayList<>(methodCount);
            for (int index = 0; index < methodCount; index++) {
                input.readUnsignedShort();
                String name = utf8Entries[input.readUnsignedShort()];
                String descriptor = utf8Entries[input.readUnsignedShort()];
                int attributeCount = input.readUnsignedShort();
                byte[] code = null;
                List<ExceptionTableEntry> exceptionTable = List.of();
                List<ParsedAnnotation> runtimeVisibleAnnotations = List.of();
                List<List<ParsedAnnotation>> parameterAnnotations = List.of();
                String signature = null;
                List<String> exceptions = List.of();
                for (int attributeIndex = 0; attributeIndex < attributeCount; attributeIndex++) {
                    String attributeName = utf8Entries[input.readUnsignedShort()];
                    int attributeLength = input.readInt();
                    if ("Code".equals(attributeName)) {
                        input.readUnsignedShort();
                        input.readUnsignedShort();
                        code = input.readNBytes(input.readInt());
                        int exceptionTableLength = input.readUnsignedShort();
                        List<ExceptionTableEntry> entries = new ArrayList<>(exceptionTableLength);
                        for (int exceptionIndex = 0; exceptionIndex < exceptionTableLength; exceptionIndex++) {
                            int startPc = input.readUnsignedShort();
                            int endPc = input.readUnsignedShort();
                            int handlerPc = input.readUnsignedShort();
                            int catchTypeIndex = input.readUnsignedShort();
                            String catchType = catchTypeIndex == 0 ? null : utf8Entries[classNameIndexes[catchTypeIndex]];
                            entries.add(new ExceptionTableEntry(startPc, endPc, handlerPc, catchType));
                        }
                        skipAttributes(input);
                        exceptionTable = entries;
                    } else if ("RuntimeVisibleAnnotations".equals(attributeName)) {
                        runtimeVisibleAnnotations = readAnnotations(input, utf8Entries, integerEntries, hasIntegerEntries);
                    } else if ("RuntimeVisibleParameterAnnotations".equals(attributeName)) {
                        parameterAnnotations = readParameterAnnotations(input, utf8Entries, integerEntries, hasIntegerEntries);
                    } else if ("Signature".equals(attributeName)) {
                        signature = utf8Entries[input.readUnsignedShort()];
                    } else if ("Exceptions".equals(attributeName)) {
                        int exceptionCount = input.readUnsignedShort();
                        List<String> exceptionNames = new ArrayList<>(exceptionCount);
                        for (int exceptionIndex = 0; exceptionIndex < exceptionCount; exceptionIndex++) {
                            exceptionNames.add(utf8Entries[classNameIndexes[input.readUnsignedShort()]]);
                        }
                        exceptions = exceptionNames;
                    } else {
                        input.skipBytes(attributeLength);
                    }
                }
                methods.add(new ParsedMethod(
                        name,
                        descriptor,
                        code,
                        exceptionTable,
                        runtimeVisibleAnnotations,
                        parameterAnnotations,
                        signature,
                        exceptions
                ));
            }

            List<ParsedAnnotation> runtimeVisibleAnnotations = readRuntimeVisibleAnnotations(
                    input,
                    utf8Entries,
                    integerEntries,
                    hasIntegerEntries
            );
            return new ParsedClass(thisClassName, superClassName, interfaces, fields, methods, runtimeVisibleAnnotations);
        }
    }

    private static List<ParsedAnnotation> readRuntimeVisibleAnnotations(
            DataInputStream input,
            String[] utf8Entries,
            int[] integerEntries,
            boolean[] hasIntegerEntries
    ) throws IOException {
        int attributeCount = input.readUnsignedShort();
        List<ParsedAnnotation> runtimeVisibleAnnotations = List.of();
        for (int index = 0; index < attributeCount; index++) {
            String attributeName = utf8Entries[input.readUnsignedShort()];
            int attributeLength = input.readInt();
            if ("RuntimeVisibleAnnotations".equals(attributeName)) {
                runtimeVisibleAnnotations = readAnnotations(input, utf8Entries, integerEntries, hasIntegerEntries);
            } else {
                input.skipBytes(attributeLength);
            }
        }
        return runtimeVisibleAnnotations;
    }

    private static List<ParsedAnnotation> readAnnotations(
            DataInputStream input,
            String[] utf8Entries,
            int[] integerEntries,
            boolean[] hasIntegerEntries
    ) throws IOException {
        int annotationCount = input.readUnsignedShort();
        List<ParsedAnnotation> annotations = new ArrayList<>(annotationCount);
        for (int index = 0; index < annotationCount; index++) {
            annotations.add(readAnnotation(input, utf8Entries, integerEntries, hasIntegerEntries));
        }
        return annotations;
    }

    private static ParsedAnnotation readAnnotation(
            DataInputStream input,
            String[] utf8Entries,
            int[] integerEntries,
            boolean[] hasIntegerEntries
    ) throws IOException {
        String typeDescriptor = utf8Entries[input.readUnsignedShort()];
        int valueCount = input.readUnsignedShort();
        Map<String, Object> values = new LinkedHashMap<>(valueCount);
        for (int index = 0; index < valueCount; index++) {
            String name = utf8Entries[input.readUnsignedShort()];
            Object value = readAnnotationValue(input, utf8Entries, integerEntries, hasIntegerEntries);
            values.put(name, value);
        }
        return new ParsedAnnotation(typeDescriptor, values);
    }

    private static Object readAnnotationValue(
            DataInputStream input,
            String[] utf8Entries,
            int[] integerEntries,
            boolean[] hasIntegerEntries
    ) throws IOException {
        int tag = input.readUnsignedByte();
        return switch (tag) {
            case 'I' -> {
                int constantPoolIndex = input.readUnsignedShort();
                assertThat(hasIntegerEntries[constantPoolIndex]).isTrue();
                yield integerEntries[constantPoolIndex];
            }
            case 'Z' -> {
                int constantPoolIndex = input.readUnsignedShort();
                assertThat(hasIntegerEntries[constantPoolIndex]).isTrue();
                yield integerEntries[constantPoolIndex] != 0;
            }
            case 'c', 's' -> utf8Entries[input.readUnsignedShort()];
            case '@' -> readAnnotation(input, utf8Entries, integerEntries, hasIntegerEntries);
            case '[' -> {
                int valueCount = input.readUnsignedShort();
                List<Object> values = new ArrayList<>(valueCount);
                for (int index = 0; index < valueCount; index++) {
                    values.add(readAnnotationValue(input, utf8Entries, integerEntries, hasIntegerEntries));
                }
                yield values;
            }
            default -> throw new IllegalArgumentException("Unsupported annotation value tag: " + (char) tag);
        };
    }

    private static List<List<ParsedAnnotation>> readParameterAnnotations(
            DataInputStream input,
            String[] utf8Entries,
            int[] integerEntries,
            boolean[] hasIntegerEntries
    ) throws IOException {
        int parameterCount = input.readUnsignedByte();
        List<List<ParsedAnnotation>> annotations = new ArrayList<>(parameterCount);
        for (int index = 0; index < parameterCount; index++) {
            annotations.add(readAnnotations(input, utf8Entries, integerEntries, hasIntegerEntries));
        }
        return annotations;
    }

    private static void skipAttributes(DataInputStream input) throws IOException {
        int attributeCount = input.readUnsignedShort();
        for (int index = 0; index < attributeCount; index++) {
            input.readUnsignedShort();
            input.skipBytes(input.readInt());
        }
    }

    private static final class ParsedClass {
        private final String thisClassName;
        private final String superClassName;
        private final List<String> interfaces;
        private final List<ParsedField> fields;
        private final List<ParsedMethod> methods;
        private final List<ParsedAnnotation> runtimeVisibleAnnotations;

        private ParsedClass(
                String thisClassName,
                String superClassName,
                List<String> interfaces,
                List<ParsedField> fields,
                List<ParsedMethod> methods,
                List<ParsedAnnotation> runtimeVisibleAnnotations
        ) {
            this.thisClassName = thisClassName;
            this.superClassName = superClassName;
            this.interfaces = interfaces;
            this.fields = fields;
            this.methods = methods;
            this.runtimeVisibleAnnotations = runtimeVisibleAnnotations;
        }

        private ParsedField field(String name, String descriptor) {
            return fields.stream()
                    .filter(field -> field.name.equals(name) && field.descriptor.equals(descriptor))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Field not found: " + name + descriptor));
        }

        private ParsedMethod method(String name, String descriptor) {
            return methods.stream()
                    .filter(method -> method.name.equals(name) && method.descriptor.equals(descriptor))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Method not found: " + name + descriptor));
        }
    }

    private static final class ParsedField {
        private final String name;
        private final String descriptor;
        private final String signature;
        private final List<ParsedAnnotation> runtimeVisibleAnnotations;

        private ParsedField(String name, String descriptor, String signature, List<ParsedAnnotation> runtimeVisibleAnnotations) {
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.runtimeVisibleAnnotations = runtimeVisibleAnnotations;
        }
    }

    private static final class ParsedMethod {
        private final String name;
        private final String descriptor;
        private final byte[] code;
        private final List<ExceptionTableEntry> exceptionTable;
        private final List<ParsedAnnotation> runtimeVisibleAnnotations;
        private final List<List<ParsedAnnotation>> parameterAnnotations;
        private final String signature;
        private final List<String> exceptions;

        private ParsedMethod(
                String name,
                String descriptor,
                byte[] code,
                List<ExceptionTableEntry> exceptionTable,
                List<ParsedAnnotation> runtimeVisibleAnnotations,
                List<List<ParsedAnnotation>> parameterAnnotations,
                String signature,
                List<String> exceptions
        ) {
            this.name = name;
            this.descriptor = descriptor;
            this.code = code;
            this.exceptionTable = exceptionTable;
            this.runtimeVisibleAnnotations = runtimeVisibleAnnotations;
            this.parameterAnnotations = parameterAnnotations;
            this.signature = signature;
            this.exceptions = exceptions;
        }
    }

    private static final class ParsedAnnotation {
        private final String typeDescriptor;
        private final Map<String, Object> values;

        private ParsedAnnotation(String typeDescriptor, Map<String, Object> values) {
            this.typeDescriptor = typeDescriptor;
            this.values = values;
        }
    }

    private static final class ExceptionTableEntry {
        private final int startPc;
        private final int endPc;
        private final int handlerPc;
        private final String catchType;

        private ExceptionTableEntry(int startPc, int endPc, int handlerPc, String catchType) {
            this.startPc = startPc;
            this.endPc = endPc;
            this.handlerPc = handlerPc;
            this.catchType = catchType;
        }
    }
}
