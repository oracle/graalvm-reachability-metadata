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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.classfilewriter.AccessFlag;
import org.jboss.classfilewriter.ClassFactory;
import org.jboss.classfilewriter.ClassField;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.ClassMethod;
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
                new String[] { "Ljava/lang/String;" }
        );
        describeCode.iload(1);
        describeCode.invokevirtual(
                StringBuilder.class.getName(),
                "append",
                "Ljava/lang/StringBuilder;",
                new String[] { "I" }
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
            for (int index = 1; index < constantPoolCount; index++) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8Entries[index] = input.readUTF();
                    case 3, 4 -> input.skipBytes(4);
                    case 5, 6 -> {
                        input.skipBytes(8);
                        index++;
                    }
                    case 7, 8, 16, 19, 20 -> classNameIndexes[index] = input.readUnsignedShort();
                    case 9, 10, 11, 12, 17, 18 -> input.skipBytes(4);
                    case 15 -> input.skipBytes(3);
                    default -> throw new IllegalArgumentException("Unsupported constant-pool tag: " + tag);
                }
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
                skipAttributes(input);
                fields.add(new ParsedField(name, descriptor));
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
                    } else {
                        input.skipBytes(attributeLength);
                    }
                }
                methods.add(new ParsedMethod(name, descriptor, code, exceptionTable));
            }

            skipAttributes(input);
            return new ParsedClass(thisClassName, superClassName, interfaces, fields, methods);
        }
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

        private ParsedClass(
                String thisClassName,
                String superClassName,
                List<String> interfaces,
                List<ParsedField> fields,
                List<ParsedMethod> methods
        ) {
            this.thisClassName = thisClassName;
            this.superClassName = superClassName;
            this.interfaces = interfaces;
            this.fields = fields;
            this.methods = methods;
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

        private ParsedField(String name, String descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }
    }

    private static final class ParsedMethod {
        private final String name;
        private final String descriptor;
        private final byte[] code;
        private final List<ExceptionTableEntry> exceptionTable;

        private ParsedMethod(String name, String descriptor, byte[] code, List<ExceptionTableEntry> exceptionTable) {
            this.name = name;
            this.descriptor = descriptor;
            this.code = code;
            this.exceptionTable = exceptionTable;
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
