/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.core.JVM;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeSetConverterInnerReflectionsTest {
    @Test
    void restoresTreeSetUsingDefaultOptimizedAddAllDetection() {
        TreeSet<String> original = treeSetOf("bravo", "alpha", "charlie");

        TreeSet<String> restoredSet = roundTripTreeSet(new XStream(), original);

        assertThat(restoredSet.comparator()).isNull();
        assertThat(restoredSet).containsExactly("alpha", "bravo", "charlie");
    }

    @Test
    void restoresTreeSetWhenOptimizedAddAllIsUnavailable() throws Exception {
        try (ChildFirstClassLoader loader = new ChildFirstClassLoader(classPathUrls())) {
            Class<?> scenarioClass = loader.loadClass(UnoptimizedTreeSetRoundTrip.class.getName());
            Runnable scenario = scenarioClass.asSubclass(Runnable.class).getConstructor().newInstance();

            scenario.run();
        } catch (InvocationTargetException e) {
            throw rethrowCause(e);
        } catch (ClassNotFoundException | UnsupportedOperationException e) {
            TreeSet<String> original = treeSetOf("delta", "alpha", "charlie");

            TreeSet<String> restoredSet = roundTripTreeSet(new XStream(), original);

            assertThat(restoredSet).containsExactly("alpha", "charlie", "delta");
        }
    }

    private static Exception rethrowCause(InvocationTargetException exception) throws Exception {
        Throwable cause = exception.getCause();
        if (cause instanceof Error) {
            throw (Error)cause;
        }
        if (cause instanceof Exception) {
            throw (Exception)cause;
        }
        throw new AssertionError(cause);
    }

    @SuppressWarnings("unchecked")
    private static TreeSet<String> roundTripTreeSet(XStream xstream, TreeSet<String> original) {
        Object restored = xstream.fromXML(xstream.toXML(original));

        assertThat(restored).isInstanceOf(TreeSet.class);
        return (TreeSet<String>)restored;
    }

    private static TreeSet<String> treeSetOf(String... values) {
        TreeSet<String> set = new TreeSet<>();
        set.addAll(Arrays.asList(values));
        return set;
    }

    private static URL[] classPathUrls() throws MalformedURLException {
        String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[entries.length];
        for (int i = 0; i < entries.length; i++) {
            urls[i] = new File(entries[i]).toURI().toURL();
        }
        return urls;
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {
        private static final String JVM_CLASS_NAME = "com.thoughtworks.xstream.core.JVM";

        private ChildFirstClassLoader(URL[] urls) {
            super(urls, TreeSetConverterInnerReflectionsTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = loadIsolatedClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (JVM_CLASS_NAME.equals(name)) {
                byte[] classBytes = readClassBytes(name);
                forceMethodToReturnFalse(classBytes, "hasOptimizedTreeSetAddAll", "()Z");
                return defineClass(name, classBytes, 0, classBytes.length);
            }
            return super.findClass(name);
        }

        private Class<?> loadIsolatedClass(String name) throws ClassNotFoundException {
            if (isIsolated(name)) {
                try {
                    return findClass(name);
                } catch (ClassNotFoundException ignored) {
                    // Delegate below.
                }
            }
            return super.loadClass(name, false);
        }

        private byte[] readClassBytes(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            URL resource = findResource(resourceName);
            if (resource == null) {
                throw new ClassNotFoundException(name);
            }
            try (InputStream input = resource.openStream()) {
                return input.readAllBytes();
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        private static void forceMethodToReturnFalse(byte[] classBytes, String methodName, String descriptor) {
            ConstantPool constantPool = ConstantPool.parse(classBytes);
            int offset = constantPool.endOffset;
            offset += 6;
            offset = skipInterfaces(classBytes, offset);
            offset = skipMembers(classBytes, offset);
            int methodCount = readUnsignedShort(classBytes, offset);
            offset += 2;
            for (int i = 0; i < methodCount; i++) {
                int nameIndex = readUnsignedShort(classBytes, offset + 2);
                int descriptorIndex = readUnsignedShort(classBytes, offset + 4);
                int attributesCount = readUnsignedShort(classBytes, offset + 6);
                offset += 8;
                for (int j = 0; j < attributesCount; j++) {
                    int attributeNameIndex = readUnsignedShort(classBytes, offset);
                    int attributeLength = readInt(classBytes, offset + 2);
                    int attributeContent = offset + 6;
                    if (methodName.equals(constantPool.utf8(nameIndex))
                            && descriptor.equals(constantPool.utf8(descriptorIndex))
                            && "Code".equals(constantPool.utf8(attributeNameIndex))) {
                        int codeLength = readInt(classBytes, attributeContent + 4);
                        int codeStart = attributeContent + 8;
                        classBytes[codeStart] = 3;
                        classBytes[codeStart + 1] = (byte)172;
                        Arrays.fill(classBytes, codeStart + 2, codeStart + codeLength, (byte)0);
                        return;
                    }
                    offset = attributeContent + attributeLength;
                }
            }
            throw new AssertionError("Could not patch method " + methodName);
        }

        private static int skipInterfaces(byte[] classBytes, int offset) {
            int interfaceCount = readUnsignedShort(classBytes, offset);
            return offset + 2 + interfaceCount * 2;
        }

        private static int skipMembers(byte[] classBytes, int offset) {
            int fieldCount = readUnsignedShort(classBytes, offset);
            offset += 2;
            for (int i = 0; i < fieldCount; i++) {
                offset = skipMember(classBytes, offset);
            }
            return offset;
        }

        private static int skipMember(byte[] classBytes, int offset) {
            int attributesCount = readUnsignedShort(classBytes, offset + 6);
            offset += 8;
            for (int i = 0; i < attributesCount; i++) {
                int attributeLength = readInt(classBytes, offset + 2);
                offset += 6 + attributeLength;
            }
            return offset;
        }

        private static int readUnsignedShort(byte[] classBytes, int offset) {
            return ((classBytes[offset] & 0xff) << 8) | (classBytes[offset + 1] & 0xff);
        }

        private static int readInt(byte[] classBytes, int offset) {
            return ((classBytes[offset] & 0xff) << 24)
                    | ((classBytes[offset + 1] & 0xff) << 16)
                    | ((classBytes[offset + 2] & 0xff) << 8)
                    | (classBytes[offset + 3] & 0xff);
        }

        private static boolean isIsolated(String name) {
            return name.startsWith("com.thoughtworks.xstream.")
                    || name.startsWith(TreeSetConverterInnerReflectionsTest.class.getName());
        }

        private static final class ConstantPool {
            private final String[] utf8Entries;
            private final int endOffset;

            private ConstantPool(String[] utf8Entries, int endOffset) {
                this.utf8Entries = utf8Entries;
                this.endOffset = endOffset;
            }

            private static ConstantPool parse(byte[] classBytes) {
                int count = readUnsignedShort(classBytes, 8);
                String[] utf8Entries = new String[count];
                int offset = 10;
                for (int i = 1; i < count; i++) {
                    int tag = classBytes[offset++] & 0xff;
                    switch (tag) {
                        case 1:
                            int length = readUnsignedShort(classBytes, offset);
                            offset += 2;
                            utf8Entries[i] = new String(classBytes, offset, length, StandardCharsets.UTF_8);
                            offset += length;
                            break;
                        case 3:
                        case 4:
                        case 9:
                        case 10:
                        case 11:
                        case 12:
                        case 18:
                            offset += 4;
                            break;
                        case 5:
                        case 6:
                            offset += 8;
                            i++;
                            break;
                        case 7:
                        case 8:
                        case 16:
                            offset += 2;
                            break;
                        case 15:
                            offset += 3;
                            break;
                        default:
                            throw new AssertionError("Unsupported constant pool tag: " + tag);
                    }
                }
                return new ConstantPool(utf8Entries, offset);
            }

            private String utf8(int index) {
                return utf8Entries[index];
            }
        }
    }

    public static final class UnoptimizedTreeSetRoundTrip implements Runnable {
        @Override
        public void run() {
            if (JVM.hasOptimizedTreeSetAddAll()) {
                throw new AssertionError("Expected TreeSet.addAll optimization to be disabled");
            }

            XStream xstream = new XStream();
            TreeSet<String> original = treeSetOf("delta", "alpha", "charlie");
            Object restored = xstream.fromXML(xstream.toXML(original));

            if (!(restored instanceof TreeSet)) {
                throw new AssertionError("Expected restored value to be a TreeSet");
            }
            List<?> restoredValues = new ArrayList<>((TreeSet<?>)restored);
            if (!restoredValues.equals(Arrays.asList("alpha", "charlie", "delta"))) {
                throw new AssertionError("Unexpected TreeSet contents: " + restoredValues);
            }
        }

        private static TreeSet<String> treeSetOf(String... values) {
            TreeSet<String> set = new TreeSet<>();
            set.addAll(Arrays.asList(values));
            return set;
        }
    }
}
