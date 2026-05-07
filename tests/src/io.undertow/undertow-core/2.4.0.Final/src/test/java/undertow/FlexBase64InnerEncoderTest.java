/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class FlexBase64InnerEncoderTest {
    private static final String FLEX_BASE64_CLASS_NAME = "io.undertow.util.FlexBase64";

    @Test
    void encodesByteArraysAndByteBuffersThroughStringConstructorShortcut() throws Exception {
        try {
            ClassLoader loader = new PatchedFlexBase64ClassLoader();
            Class<?> flexBase64Class = Class.forName(FLEX_BASE64_CLASS_NAME, true, loader);
            Method encodeByteArray = flexBase64Class.getMethod(
                    "encodeString", byte[].class, int.class, int.class, boolean.class);
            Method encodeByteBuffer = flexBase64Class.getMethod("encodeString", ByteBuffer.class, boolean.class);

            byte[] source = "hello".getBytes(StandardCharsets.US_ASCII);
            String byteArrayResult = (String) encodeByteArray.invoke(null, source, 1, 4, false);
            ByteBuffer byteBuffer = ByteBuffer.wrap(source);
            String byteBufferResult = (String) encodeByteBuffer.invoke(null, byteBuffer, false);

            assertThat(byteArrayResult).isEqualTo("ZWxs");
            assertThat(byteBufferResult).isEqualTo("aGVsbG8=");
            assertThat(byteBuffer.position()).isEqualTo(byteBuffer.limit());
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            throw exception;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class PatchedFlexBase64ClassLoader extends ClassLoader {
        private static final byte LDC = 0x12;
        private static final byte ICONST_1 = 0x04;
        private static final byte ICONST_2 = 0x05;
        private static final byte ANEWARRAY = (byte) 0xbd;
        private static final byte DUP = 0x59;
        private static final byte ICONST_0 = 0x03;
        private static final byte AASTORE = 0x53;
        private static final byte GETSTATIC = (byte) 0xb2;
        private static final byte INVOKEVIRTUAL = (byte) 0xb6;
        private static final byte NOP = 0x00;

        private PatchedFlexBase64ClassLoader() {
            super(FlexBase64InnerEncoderTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(FLEX_BASE64_CLASS_NAME) || name.startsWith(FLEX_BASE64_CLASS_NAME + "$")) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = defineActiveClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
            return super.loadClass(name, resolve);
        }

        private Class<?> defineActiveClass(String className) throws ClassNotFoundException {
            String resourceName = className.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(className);
                }
                byte[] classBytes = inputStream.readAllBytes();
                if (FLEX_BASE64_CLASS_NAME.equals(className)) {
                    classBytes = patchStringConstructorLookup(classBytes);
                }
                return defineClass(className, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(className, exception);
            }
        }

        private static byte[] patchStringConstructorLookup(byte[] classBytes) throws ClassNotFoundException {
            int constructorLookupOffset = findStringConstructorLookupOffset(classBytes);
            byte[] patchedBytes = classBytes.clone();
            patchedBytes[constructorLookupOffset + 2] = ICONST_1;
            for (int offset = constructorLookupOffset + 11; offset <= constructorLookupOffset + 16; offset++) {
                patchedBytes[offset] = NOP;
            }
            return patchedBytes;
        }

        private static int findStringConstructorLookupOffset(byte[] classBytes) throws ClassNotFoundException {
            int matchOffset = -1;
            for (int offset = 0; offset <= classBytes.length - 18; offset++) {
                if (isStringConstructorLookup(classBytes, offset)) {
                    if (matchOffset != -1) {
                        throw new ClassNotFoundException("Multiple String constructor lookup patterns found");
                    }
                    matchOffset = offset;
                }
            }
            if (matchOffset == -1) {
                throw new ClassNotFoundException("String constructor lookup pattern not found");
            }
            return matchOffset;
        }

        private static boolean isStringConstructorLookup(byte[] classBytes, int offset) {
            return classBytes[offset] == LDC
                    && classBytes[offset + 2] == ICONST_2
                    && classBytes[offset + 3] == ANEWARRAY
                    && classBytes[offset + 6] == DUP
                    && classBytes[offset + 7] == ICONST_0
                    && classBytes[offset + 8] == LDC
                    && classBytes[offset + 10] == AASTORE
                    && classBytes[offset + 11] == DUP
                    && classBytes[offset + 12] == ICONST_1
                    && classBytes[offset + 13] == GETSTATIC
                    && classBytes[offset + 16] == AASTORE
                    && classBytes[offset + 17] == INVOKEVIRTUAL;
        }
    }
}
