/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mozilla.classfile.ByteCode;
import org.mozilla.classfile.ClassFileWriter;

public class TypeInfoTest {
    private static final String CLASS_FILE_WRITER_NAME = "org.mozilla.classfile.ClassFileWriter";
    private static final String CLASS_FILE_WRITER_RESOURCE = "org/mozilla/classfile/ClassFileWriter.class";

    @Test
    void mergesDifferentReferenceTypesWhenBuildingStackMapTable() throws Exception {
        runAllowingNativeImageUnsupportedClassLoading(() -> {
            try (StackMapClassLoader classLoader = new StackMapClassLoader(classPathUrls())) {
                Class<?> writerClass = Class.forName(CLASS_FILE_WRITER_NAME, true, classLoader);
                Object writer = newWriter(writerClass);

                short methodFlags = ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_STATIC;
                invoke(writerClass, writer, "startMethod", parameterTypes(String.class, String.class, short.class),
                        "choose", "(Z)Ljava/lang/Object;", methodFlags);

                int falseBranch = (Integer) invoke(writerClass, writer, "acquireLabel", parameterTypes());
                int mergePoint = (Integer) invoke(writerClass, writer, "acquireLabel", parameterTypes());

                invoke(writerClass, writer, "addILoad", parameterTypes(int.class), 0);
                invoke(writerClass, writer, "add", parameterTypes(int.class, int.class), ByteCode.IFEQ, falseBranch);

                invoke(writerClass, writer, "add", parameterTypes(int.class), ByteCode.ICONST_1);
                Class<?>[] invokeTypes = parameterTypes(int.class, String.class, String.class, String.class);
                invoke(writerClass, writer, "addInvoke", invokeTypes, ByteCode.INVOKESTATIC,
                        "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
                invoke(writerClass, writer, "add", parameterTypes(int.class, int.class), ByteCode.GOTO, mergePoint);

                Class<?>[] labelTypes = parameterTypes(int.class, short.class);
                invoke(writerClass, writer, "markLabel", labelTypes, falseBranch, (short) 0);
                invoke(writerClass, writer, "add", parameterTypes(int.class), ByteCode.ICONST_0);
                invoke(writerClass, writer, "addInvoke", invokeTypes, ByteCode.INVOKESTATIC,
                        "java/lang/String", "valueOf", "(I)Ljava/lang/String;");

                invoke(writerClass, writer, "markLabel", labelTypes, mergePoint, (short) 1);
                invoke(writerClass, writer, "add", parameterTypes(int.class), ByteCode.ARETURN);
                invoke(writerClass, writer, "stopMethod", parameterTypes(short.class), (short) 1);

                byte[] classBytes = (byte[]) invoke(writerClass, writer, "toByteArray", parameterTypes());

                assertThat(classBytes).hasSizeGreaterThan(8);
                assertThat(classBytes[0]).isEqualTo((byte) 0xCA);
                assertThat(classBytes[1]).isEqualTo((byte) 0xFE);
                assertThat(classBytes[2]).isEqualTo((byte) 0xBA);
                assertThat(classBytes[3]).isEqualTo((byte) 0xBE);
                assertThat(classBytes[7]).isEqualTo((byte) 50);
            }
        });
    }

    private static Object newWriter(Class<?> writerClass) throws ReflectiveOperationException {
        Constructor<?> constructor = writerClass.getConstructor(String.class, String.class, String.class);
        return constructor.newInstance(
                "generated.TypeInfoMergeCoverage", "java.lang.Object", "TypeInfoMergeCoverage.java");
    }

    private static Object invoke(Class<?> type, Object target, String methodName, Class<?>[] parameterTypes,
            Object... arguments) throws ReflectiveOperationException {
        Method method = type.getMethod(methodName, parameterTypes);
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw exception;
        }
    }

    private static Class<?>[] parameterTypes(Class<?>... types) {
        return types;
    }

    private static URL[] classPathUrls() throws IOException {
        String classPath = System.getProperty("java.class.path");
        String[] entries = classPath.split(File.pathSeparator);
        List<URL> urls = new ArrayList<>(entries.length);
        for (String entry : entries) {
            urls.add(new File(entry).toURI().toURL());
        }
        return urls.toArray(URL[]::new);
    }

    private static void runAllowingNativeImageUnsupportedClassLoading(ThrowingRunnable action) throws Exception {
        try {
            action.run();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class StackMapClassLoader extends URLClassLoader {
        private StackMapClassLoader(URL[] urls) {
            super(urls, TypeInfoTest.class.getClassLoader());
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            InputStream stream = super.getResourceAsStream(name);
            if (!CLASS_FILE_WRITER_RESOURCE.equals(name) || stream == null) {
                return stream;
            }
            try {
                byte[] classBytes = readAllBytes(stream);
                classBytes[4] = 0;
                classBytes[5] = 0;
                classBytes[6] = 0;
                classBytes[7] = 50;
                return new ByteArrayInputStream(classBytes);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to patch ClassFileWriter class file version", exception);
            }
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("org.mozilla.classfile.")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException exception) {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private static byte[] readAllBytes(InputStream stream) throws IOException {
            try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        }
    }
}
