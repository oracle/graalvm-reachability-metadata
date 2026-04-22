/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeLibraryLoaderTest {

    private static final String CONSCRYPT_PACKAGE = "org.conscrypt.";
    private static final String NATIVE_LIBRARY_LOADER_CLASS_NAME = "org.conscrypt.NativeLibraryLoader";
    private static final String HIDDEN_HELPER_CLASS_NAME = "org.conscrypt.NativeLibraryUtil";
    private static final String LOAD_FIRST_AVAILABLE_METHOD_NAME = "loadFirstAvailable";
    private static final String PRIMARY_LIBRARY_RESOURCE_NAME = "META-INF/native/libconscrypt_openjdk_jni.so";
    private static final String OSX_FALLBACK_LIBRARY_RESOURCE_NAME = "META-INF/native/libconscrypt_openjdk_jni.jnilib";
    private static final String OSX_DYNLIB_FALLBACK_LIBRARY_RESOURCE_NAME = "META-INF/native/libconscrypt_openjdk_jni.dynlib";
    private static final String OSX_NAME = "Mac OS X";

    @TempDir
    Path tempDir;

    @Test
    void loadsFromOsxJnilibFallbackResourceWhenPrimaryResourceIsMissing() throws Exception {
        String previousOsName = System.getProperty("os.name");
        Path fallbackLibrary = tempDir.resolve("libconscrypt_openjdk_jni.jnilib");
        Files.write(fallbackLibrary, new byte[] {0x01, 0x23, 0x45, 0x67});

        System.setProperty("os.name", OSX_NAME);
        try (IsolatedConscryptClassLoader isolatedConscryptClassLoader = new IsolatedConscryptClassLoader(codeSourceUrl("org.conscrypt.Conscrypt"))) {
            NativeLibraryLookupClassLoader targetClassLoader = new NativeLibraryLookupClassLoader(
                    fallbackLibrary.toUri().toURL(),
                    true,
                    null,
                    false);
            List<Object> results = new ArrayList<>();

            boolean loaded = invokeLoadFirstAvailable(
                    isolatedConscryptClassLoader,
                    targetClassLoader,
                    results,
                    "conscrypt_openjdk_jni"
            );

            assertThat(loaded).isFalse();
            assertThat(results).isNotEmpty();
            assertThat(targetClassLoader.primaryResourceLookupCount).isEqualTo(1);
            assertThat(targetClassLoader.osxFallbackResourceLookupCount).isEqualTo(1);
            assertThat(targetClassLoader.helperClassLoadAttempts).isGreaterThan(0);
        } finally {
            restoreSystemProperty("os.name", previousOsName);
        }
    }

    @Test
    void loadsFromOsxDynlibFallbackResourceWhenMappedLibraryNameEndsWithJniLib() throws Exception {
        String previousOsName = System.getProperty("os.name");
        Path fallbackLibrary = tempDir.resolve("libconscrypt_openjdk_jni.dynlib");
        Files.write(fallbackLibrary, new byte[] {0x10, 0x32, 0x54, 0x76});

        System.setProperty("os.name", OSX_NAME);
        try (IsolatedConscryptClassLoader isolatedConscryptClassLoader = new IsolatedConscryptClassLoader(
                codeSourceUrl("org.conscrypt.Conscrypt"),
                patchUtf8Constant(readClassBytes(NATIVE_LIBRARY_LOADER_CLASS_NAME), ".jnilib", ".so"))) {
            NativeLibraryLookupClassLoader targetClassLoader = new NativeLibraryLookupClassLoader(
                    null,
                    false,
                    fallbackLibrary.toUri().toURL(),
                    true);
            List<Object> results = new ArrayList<>();

            boolean loaded = invokeLoadFirstAvailable(
                    isolatedConscryptClassLoader,
                    targetClassLoader,
                    results,
                    "conscrypt_openjdk_jni"
            );

            assertThat(loaded).isFalse();
            assertThat(results).isNotEmpty();
            assertThat(targetClassLoader.primaryResourceLookupCount).isEqualTo(1);
            assertThat(targetClassLoader.osxDynlibResourceLookupCount).isEqualTo(1);
            assertThat(targetClassLoader.helperClassLoadAttempts).isGreaterThan(0);
        } finally {
            restoreSystemProperty("os.name", previousOsName);
        }
    }

    @Test
    void readsHelperClassBytesWhenTargetLoaderCannotLoadNativeLibraryUtil() throws Exception {
        NativeLibraryLookupClassLoader targetClassLoader = new NativeLibraryLookupClassLoader(null, false, null, false);
        List<Object> results = new ArrayList<>();

        boolean loaded = invokeLoadFirstAvailable(
                NativeLibraryLoaderTest.class.getClassLoader(),
                targetClassLoader,
                results,
                "conscrypt-missing-native-library"
        );

        assertThat(loaded).isFalse();
        assertThat(results).isNotEmpty();
        assertThat(targetClassLoader.helperClassLoadAttempts).isGreaterThan(0);
    }

    private static boolean invokeLoadFirstAvailable(
            ClassLoader conscryptClassLoader,
            ClassLoader targetClassLoader,
            List<Object> results,
            String... libraryNames) throws Exception {
        Class<?> nativeLibraryLoaderClass = Class.forName(NATIVE_LIBRARY_LOADER_CLASS_NAME, true, conscryptClassLoader);
        Method loadFirstAvailable = nativeLibraryLoaderClass.getDeclaredMethod(
                LOAD_FIRST_AVAILABLE_METHOD_NAME,
                ClassLoader.class,
                List.class,
                String[].class
        );
        loadFirstAvailable.setAccessible(true);
        try {
            return (boolean) loadFirstAvailable.invoke(null, targetClassLoader, results, (Object) libraryNames);
        } catch (InvocationTargetException invocationTargetException) {
            Throwable targetException = invocationTargetException.getTargetException();
            if (targetException instanceof Exception exception) {
                throw exception;
            }
            if (targetException instanceof Error error) {
                throw error;
            }
            throw invocationTargetException;
        }
    }

    private static URL codeSourceUrl(String className) throws Exception {
        Class<?> type = Class.forName(className, false, NativeLibraryLoaderTest.class.getClassLoader());
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static byte[] readClassBytes(String className) throws Exception {
        String resourceName = className.replace('.', '/') + ".class";
        try (InputStream inputStream = NativeLibraryLoaderTest.class.getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(inputStream).isNotNull();
            return inputStream.readAllBytes();
        }
    }

    private static byte[] patchUtf8Constant(byte[] classBytes, String oldValue, String newValue) throws Exception {
        try (DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(classBytes));
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(classBytes.length + 32);
                DataOutputStream outputStream = new DataOutputStream(byteArrayOutputStream)) {
            int replacementCount = 0;

            outputStream.writeInt(inputStream.readInt());
            outputStream.writeShort(inputStream.readUnsignedShort());
            outputStream.writeShort(inputStream.readUnsignedShort());

            int constantPoolCount = inputStream.readUnsignedShort();
            outputStream.writeShort(constantPoolCount);

            for (int index = 1; index < constantPoolCount; index++) {
                int tag = inputStream.readUnsignedByte();
                outputStream.writeByte(tag);
                switch (tag) {
                    case 1 -> {
                        int length = inputStream.readUnsignedShort();
                        byte[] bytes = inputStream.readNBytes(length);
                        String value = new String(bytes, StandardCharsets.UTF_8);
                        byte[] patchedBytes = value.equals(oldValue) ? newValue.getBytes(StandardCharsets.UTF_8) : bytes;
                        if (value.equals(oldValue)) {
                            replacementCount++;
                        }
                        outputStream.writeShort(patchedBytes.length);
                        outputStream.write(patchedBytes);
                    }
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> outputStream.writeInt(inputStream.readInt());
                    case 5, 6 -> {
                        outputStream.writeLong(inputStream.readLong());
                        index++;
                    }
                    case 7, 8, 16, 19, 20 -> outputStream.writeShort(inputStream.readUnsignedShort());
                    case 15 -> {
                        outputStream.writeByte(inputStream.readUnsignedByte());
                        outputStream.writeShort(inputStream.readUnsignedShort());
                    }
                    default -> throw new IllegalStateException("Unsupported constant pool tag: " + tag);
                }
            }

            outputStream.write(inputStream.readAllBytes());
            assertThat(replacementCount).isEqualTo(1);
            return byteArrayOutputStream.toByteArray();
        }
    }

    private static void restoreSystemProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }

    private static final class NativeLibraryLookupClassLoader extends ClassLoader {

        private final URL osxFallbackLibraryUrl;
        private final boolean exposeOsxFallbackResource;
        private final URL osxDynlibFallbackLibraryUrl;
        private final boolean exposeOsxDynlibFallbackResource;
        private int primaryResourceLookupCount;
        private int osxFallbackResourceLookupCount;
        private int osxDynlibResourceLookupCount;
        private int helperClassLoadAttempts;

        private NativeLibraryLookupClassLoader(
                URL osxFallbackLibraryUrl,
                boolean exposeOsxFallbackResource,
                URL osxDynlibFallbackLibraryUrl,
                boolean exposeOsxDynlibFallbackResource) {
            super(ClassLoader.getPlatformClassLoader());
            this.osxFallbackLibraryUrl = osxFallbackLibraryUrl;
            this.exposeOsxFallbackResource = exposeOsxFallbackResource;
            this.osxDynlibFallbackLibraryUrl = osxDynlibFallbackLibraryUrl;
            this.exposeOsxDynlibFallbackResource = exposeOsxDynlibFallbackResource;
        }

        @Override
        public URL getResource(String name) {
            if (PRIMARY_LIBRARY_RESOURCE_NAME.equals(name)) {
                primaryResourceLookupCount++;
                return null;
            }
            if (exposeOsxFallbackResource && OSX_FALLBACK_LIBRARY_RESOURCE_NAME.equals(name)) {
                osxFallbackResourceLookupCount++;
                return osxFallbackLibraryUrl;
            }
            if (exposeOsxDynlibFallbackResource && OSX_DYNLIB_FALLBACK_LIBRARY_RESOURCE_NAME.equals(name)) {
                osxDynlibResourceLookupCount++;
                return osxDynlibFallbackLibraryUrl;
            }
            return null;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (HIDDEN_HELPER_CLASS_NAME.equals(name)) {
                helperClassLoadAttempts++;
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class IsolatedConscryptClassLoader extends URLClassLoader {

        private final byte[] patchedNativeLibraryLoaderClassBytes;

        private IsolatedConscryptClassLoader(URL jarUrl, byte[] patchedNativeLibraryLoaderClassBytes) {
            super(new URL[] {jarUrl}, ClassLoader.getPlatformClassLoader());
            this.patchedNativeLibraryLoaderClassBytes = patchedNativeLibraryLoaderClassBytes;
        }

        private IsolatedConscryptClassLoader(URL jarUrl) {
            this(jarUrl, null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (NATIVE_LIBRARY_LOADER_CLASS_NAME.equals(name) && patchedNativeLibraryLoaderClassBytes != null) {
                        loadedClass = defineClass(name, patchedNativeLibraryLoaderClassBytes, 0, patchedNativeLibraryLoaderClassBytes.length);
                    } else if (name.startsWith(CONSCRYPT_PACKAGE)) {
                        try {
                            loadedClass = findClass(name);
                        } catch (ClassNotFoundException ignored) {
                            loadedClass = super.loadClass(name, false);
                        }
                    } else {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }
}
