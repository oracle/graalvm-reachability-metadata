/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.graalvm.internal.tck.NativeImageSupport;
import org.h2.util.MemoryUnmapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class MemoryUnmapperTest {
    private static final String FALLBACK_INVOKER_NAME = "h2.MemoryUnmapperTest$FallbackMemoryUnmapperInvoker";
    private static final String MEMORY_UNMAPPER_NAME = "org.h2.util.MemoryUnmapper";
    private static final String SYS_PROPERTIES_NAME = "org.h2.engine.SysProperties";
    private static final String UNSAFE_NAME = "sun.misc.Unsafe";

    @Test
    void unmapsDirectByteBufferWhenCleanerHackIsEnabled() {
        assertThat(System.getProperty("h2.nioCleanerHack")).isEqualTo("true");

        ByteBuffer directBuffer = ByteBuffer.allocateDirect(16);
        directBuffer.putInt(42);

        MemoryUnmapper.unmap(directBuffer);
    }

    @Test
    void exercisesJava8CleanerFallbackWhenInvokeCleanerIsUnavailable(@TempDir Path tempDirectory) throws Exception {
        assertThat(System.getProperty("h2.nioCleanerHack")).isEqualTo("true");

        try {
            Path servicesDirectory = Files.createDirectories(tempDirectory.resolve("META-INF/services"));
            Files.writeString(servicesDirectory.resolve(UnmapperInvoker.class.getName()), FALLBACK_INVOKER_NAME);

            URL h2Jar = MemoryUnmapper.class.getProtectionDomain().getCodeSource().getLocation();
            URL testClasses = MemoryUnmapperTest.class.getProtectionDomain().getCodeSource().getLocation();
            try (Java8FallbackClassLoader classLoader = new Java8FallbackClassLoader(
                    new URL[] {tempDirectory.toUri().toURL(), testClasses, h2Jar},
                    MemoryUnmapperTest.class.getClassLoader())) {
                UnmapperInvoker invoker = ServiceLoader.load(UnmapperInvoker.class, classLoader)
                        .findFirst()
                        .orElseThrow();
                ByteBuffer directBuffer = ByteBuffer.allocateDirect(16);
                directBuffer.putInt(42);

                invoker.unmap(directBuffer);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void returnsFalseForHeapByteBufferWhenCleanerHackIsEnabled() {
        assertThat(System.getProperty("h2.nioCleanerHack")).isEqualTo("true");

        ByteBuffer heapBuffer = ByteBuffer.allocate(16);
        heapBuffer.putInt(42);

        boolean unmapped = MemoryUnmapper.unmap(heapBuffer);

        assertThat(unmapped).isFalse();
    }

    public interface UnmapperInvoker {
        boolean unmap(ByteBuffer buffer);
    }

    public static final class FallbackMemoryUnmapperInvoker implements UnmapperInvoker {
        public FallbackMemoryUnmapperInvoker() {
        }

        @Override
        public boolean unmap(ByteBuffer buffer) {
            return MemoryUnmapper.unmap(buffer);
        }
    }

    private static final class Java8FallbackClassLoader extends URLClassLoader {
        private Java8FallbackClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && isChildFirstClass(name)) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        // Fall back to the parent for all non-test dependencies.
                    }
                }
                if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (SYS_PROPERTIES_NAME.equals(name)) {
                byte[] classBytes = sysPropertiesClassBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            }
            if (UNSAFE_NAME.equals(name)) {
                byte[] classBytes = unsafeWithoutInvokeCleanerClassBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            }
            return super.findClass(name);
        }

        private static boolean isChildFirstClass(String name) {
            return MEMORY_UNMAPPER_NAME.equals(name)
                    || SYS_PROPERTIES_NAME.equals(name)
                    || UNSAFE_NAME.equals(name)
                    || FALLBACK_INVOKER_NAME.equals(name);
        }
    }

    private static byte[] sysPropertiesClassBytes() {
        try {
            ClassFileWriter writer = new ClassFileWriter("org/h2/engine/SysProperties", "java/lang/Object");
            int fieldName = writer.utf8("NIO_CLEANER_HACK");
            int fieldDescriptor = writer.utf8("Z");
            int constantValueAttribute = writer.utf8("ConstantValue");
            int trueConstant = writer.integer(1);
            writer.writeHeader();
            writer.writeFieldsCount(1);
            writer.writeField(0x0019, fieldName, fieldDescriptor, constantValueAttribute, trueConstant);
            writer.writeMethodsCount(0);
            writer.writeClassAttributesCount(0);
            return writer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create SysProperties test class", e);
        }
    }

    private static byte[] unsafeWithoutInvokeCleanerClassBytes() {
        try {
            ClassFileWriter writer = new ClassFileWriter("sun/misc/Unsafe", "java/lang/Object");
            int fieldName = writer.utf8("theUnsafe");
            int fieldDescriptor = writer.utf8("Lsun/misc/Unsafe;");
            int constructorName = writer.utf8("<init>");
            int voidDescriptor = writer.utf8("()V");
            int codeAttribute = writer.utf8("Code");
            int objectConstructor = writer.methodRef("java/lang/Object", "<init>", "()V");
            int unsafeConstructor = writer.methodRef("sun/misc/Unsafe", "<init>", "()V");
            int unsafeField = writer.fieldRef("sun/misc/Unsafe", "theUnsafe", "Lsun/misc/Unsafe;");
            int classInitializerName = writer.utf8("<clinit>");
            writer.writeHeader();
            writer.writeFieldsCount(1);
            writer.writeFieldWithoutAttributes(0x0019, fieldName, fieldDescriptor);
            writer.writeMethodsCount(2);
            writer.writeMethod(0x0002, constructorName, voidDescriptor, codeAttribute, 1, 1,
                    new byte[] {
                            0x2a, (byte) 0xb7, highByte(objectConstructor), lowByte(objectConstructor), (byte) 0xb1
                    });
            writer.writeMethod(0x0008, classInitializerName, voidDescriptor, codeAttribute, 2, 0,
                    new byte[] {(byte) 0xbb, highByte(writer.thisClassIndex()), lowByte(writer.thisClassIndex()), 0x59,
                            (byte) 0xb7, highByte(unsafeConstructor), lowByte(unsafeConstructor), (byte) 0xb3,
                            highByte(unsafeField), lowByte(unsafeField), (byte) 0xb1});
            writer.writeClassAttributesCount(0);
            return writer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create Unsafe test class", e);
        }
    }

    private static byte highByte(int value) {
        return (byte) (value >>> 8);
    }

    private static byte lowByte(int value) {
        return (byte) value;
    }

    private static final class ClassFileWriter {
        private static final int JAVA_8_VERSION = 52;

        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final DataOutputStream data = new DataOutputStream(output);
        private final ByteArrayOutputStream constantPool = new ByteArrayOutputStream();
        private final DataOutputStream constants = new DataOutputStream(constantPool);
        private int constantPoolCount = 1;
        private final int thisClass;
        private final int superClass;

        private ClassFileWriter(String className, String superClassName) throws IOException {
            thisClass = classInfo(className);
            superClass = classInfo(superClassName);
        }

        private int thisClassIndex() {
            return thisClass;
        }

        private int utf8(String value) throws IOException {
            constants.writeByte(1);
            constants.writeUTF(value);
            return constantPoolCount++;
        }

        private int integer(int value) throws IOException {
            constants.writeByte(3);
            constants.writeInt(value);
            return constantPoolCount++;
        }

        private int classInfo(String className) throws IOException {
            int name = utf8(className);
            constants.writeByte(7);
            constants.writeShort(name);
            return constantPoolCount++;
        }

        private int nameAndType(String name, String descriptor) throws IOException {
            int nameIndex = utf8(name);
            int descriptorIndex = utf8(descriptor);
            constants.writeByte(12);
            constants.writeShort(nameIndex);
            constants.writeShort(descriptorIndex);
            return constantPoolCount++;
        }

        private int methodRef(String owner, String name, String descriptor) throws IOException {
            int ownerClass = classInfo(owner);
            int nameAndType = nameAndType(name, descriptor);
            constants.writeByte(10);
            constants.writeShort(ownerClass);
            constants.writeShort(nameAndType);
            return constantPoolCount++;
        }

        private int fieldRef(String owner, String name, String descriptor) throws IOException {
            int ownerClass = classInfo(owner);
            int nameAndType = nameAndType(name, descriptor);
            constants.writeByte(9);
            constants.writeShort(ownerClass);
            constants.writeShort(nameAndType);
            return constantPoolCount++;
        }

        private void writeHeader() throws IOException {
            data.writeInt(0xcafebabe);
            data.writeShort(0);
            data.writeShort(JAVA_8_VERSION);
            data.writeShort(constantPoolCount);
            data.write(constantPool.toByteArray());
            data.writeShort(0x0031);
            data.writeShort(thisClass);
            data.writeShort(superClass);
            data.writeShort(0);
        }

        private void writeFieldsCount(int count) throws IOException {
            data.writeShort(count);
        }

        private void writeField(int access, int name, int descriptor, int attributeName, int constantValue)
                throws IOException {
            data.writeShort(access);
            data.writeShort(name);
            data.writeShort(descriptor);
            data.writeShort(1);
            data.writeShort(attributeName);
            data.writeInt(2);
            data.writeShort(constantValue);
        }

        private void writeFieldWithoutAttributes(int access, int name, int descriptor) throws IOException {
            data.writeShort(access);
            data.writeShort(name);
            data.writeShort(descriptor);
            data.writeShort(0);
        }

        private void writeMethodsCount(int count) throws IOException {
            data.writeShort(count);
        }

        private void writeMethod(int access, int name, int descriptor, int codeAttribute, int maxStack, int maxLocals,
                byte[] code) throws IOException {
            data.writeShort(access);
            data.writeShort(name);
            data.writeShort(descriptor);
            data.writeShort(1);
            data.writeShort(codeAttribute);
            data.writeInt(12 + code.length);
            data.writeShort(maxStack);
            data.writeShort(maxLocals);
            data.writeInt(code.length);
            data.write(code);
            data.writeShort(0);
            data.writeShort(0);
        }

        private void writeClassAttributesCount(int count) throws IOException {
            data.writeShort(count);
        }

        private byte[] toByteArray() {
            return output.toByteArray();
        }
    }
}
