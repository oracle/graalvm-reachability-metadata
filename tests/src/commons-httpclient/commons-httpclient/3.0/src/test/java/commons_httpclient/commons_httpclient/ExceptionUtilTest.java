/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;

import org.apache.commons.httpclient.util.ExceptionUtil;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExceptionUtilTest {
    private static final String EXCEPTION_UTIL_CLASS_NAME =
            "org.apache.commons.httpclient.util.ExceptionUtil";
    private static final String EXCEPTION_UTIL_RESOURCE =
            "org/apache/commons/httpclient/util/ExceptionUtil.class";
    private static final String THROWABLE_CLASS_CACHE_FIELD_NAME = "class$java$lang$Throwable";

    @Test
    @Order(1)
    void instrumentableCopyInitializesLoggerThroughLegacyClassHelper() throws Throwable {
        try (ExceptionUtilClassLoader classLoader = newInstrumentableExceptionUtilClassLoader()) {
            Class<?> exceptionUtilClass = Class.forName(
                    EXCEPTION_UTIL_CLASS_NAME,
                    true,
                    classLoader);
            MethodHandle classLookup = legacyClassLiteralHelper(exceptionUtilClass);
            Class<?> resolvedExceptionUtilClass = (Class<?>) classLookup.invoke(EXCEPTION_UTIL_CLASS_NAME);
            Class<?> resolvedThrowableClass = (Class<?>) classLookup.invoke("java.lang.Throwable");

            assertThat(exceptionUtilClass.getName()).isEqualTo(EXCEPTION_UTIL_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(exceptionUtilClass).isSameAs(ExceptionUtil.class);
            } else {
                assertThat(exceptionUtilClass.getClassLoader()).isSameAs(classLoader);
            }
            assertThat(resolvedExceptionUtilClass).isSameAs(exceptionUtilClass);
            assertThat(resolvedThrowableClass).isSameAs(Throwable.class);
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    @Order(2)
    void publicMethodsInitializeCausesAndIdentifySocketTimeouts() {
        IllegalStateException exception = new IllegalStateException("request failed");
        IllegalArgumentException cause = new IllegalArgumentException("invalid state");

        ExceptionUtil.initCause(exception, cause);

        assertThat(exception).hasCause(cause);
        assertThat(ExceptionUtil.isSocketTimeoutException(new SocketTimeoutException("read timed out")))
                .isTrue();
        assertThat(ExceptionUtil.isSocketTimeoutException(new InterruptedIOException("interrupted")))
                .isFalse();
    }

    @Test
    @Order(3)
    void getInitCauseMethodReloadsThrowableClassWhenLegacyCacheIsEmpty() throws Throwable {
        VarHandle throwableClassCache = throwableClassCache();
        Object previousValue = throwableClassCache.get();

        try {
            throwableClassCache.set(null);
            MethodHandle getInitCauseMethod = privateStaticMethod(
                    ExceptionUtil.class,
                    "getInitCauseMethod",
                    MethodType.methodType(Method.class));

            Method initCauseMethod = (Method) getInitCauseMethod.invoke();

            assertThat(initCauseMethod.getName()).isEqualTo("initCause");
            assertThat(throwableClassCache.get()).isSameAs(Throwable.class);
        } finally {
            throwableClassCache.set(previousValue);
        }
    }

    @Test
    @Order(4)
    void legacyClassLiteralHelperResolvesKnownTypes() throws Throwable {
        MethodHandle classLookup = legacyClassLiteralHelper(ExceptionUtil.class);

        Class<?> resolvedExceptionUtilClass = (Class<?>) classLookup.invoke(EXCEPTION_UTIL_CLASS_NAME);
        Class<?> resolvedThrowableClass = (Class<?>) classLookup.invoke("java.lang.Throwable");

        assertThat(resolvedExceptionUtilClass).isSameAs(ExceptionUtil.class);
        assertThat(resolvedThrowableClass).isSameAs(Throwable.class);
    }

    @Test
    @Order(5)
    void legacyClassLiteralHelperWrapsMissingClassFailures() throws Throwable {
        MethodHandle classLookup = legacyClassLiteralHelper(ExceptionUtil.class);

        assertThatThrownBy(() -> classLookup.invoke("example.missing.ExceptionUtil"))
                .isInstanceOf(NoClassDefFoundError.class)
                .hasMessageContaining("example.missing.ExceptionUtil");
    }

    private static MethodHandle legacyClassLiteralHelper(Class<?> exceptionUtilClass)
            throws NoSuchMethodException, IllegalAccessException {
        return privateStaticMethod(
                exceptionUtilClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }

    private static MethodHandle privateStaticMethod(
            Class<?> owner,
            String name,
            MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
        return lookup.findStatic(owner, name, methodType);
    }

    private static VarHandle throwableClassCache() throws IllegalAccessException, NoSuchFieldException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ExceptionUtil.class, MethodHandles.lookup());
        return lookup.findStaticVarHandle(
                ExceptionUtil.class,
                THROWABLE_CLASS_CACHE_FIELD_NAME,
                Class.class);
    }

    private static ExceptionUtilClassLoader newInstrumentableExceptionUtilClassLoader()
            throws IOException {
        URL location = ExceptionUtil.class.getProtectionDomain().getCodeSource().getLocation();
        return new ExceptionUtilClassLoader(
                new URL[] {location},
                ExceptionUtilTest.class.getClassLoader(),
                exceptionUtilClassBytesWithVisibleClassHelperLine());
    }

    private static byte[] exceptionUtilClassBytesWithVisibleClassHelperLine() throws IOException {
        ClassLoader classLoader = ExceptionUtil.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(EXCEPTION_UTIL_RESOURCE)) {
            if (input == null) {
                return null;
            }
            byte[] classBytes = input.readAllBytes();
            replaceSyntheticAttributeName(classBytes);
            return classBytes;
        }
    }

    private static void replaceSyntheticAttributeName(byte[] classBytes) {
        int constantPoolCount = unsignedShort(classBytes, 8);
        int offset = 10;
        byte[] synthetic = "Synthetic".getBytes(StandardCharsets.ISO_8859_1);
        byte[] replacement = "Synthetix".getBytes(StandardCharsets.ISO_8859_1);
        for (int index = 1; index < constantPoolCount; index++) {
            int tag = classBytes[offset++] & 0xff;
            switch (tag) {
                case 1:
                    int length = unsignedShort(classBytes, offset);
                    offset += 2;
                    if (length == synthetic.length
                            && startsWith(classBytes, synthetic, offset)) {
                        System.arraycopy(replacement, 0, classBytes, offset, replacement.length);
                    }
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
                    constantPoolCount--;
                    break;
                case 7:
                case 8:
                case 16:
                case 19:
                case 20:
                    offset += 2;
                    break;
                case 15:
                    offset += 3;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported constant pool tag: " + tag);
            }
        }
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static boolean startsWith(byte[] source, byte[] target, int sourceIndex) {
        for (int targetIndex = 0; targetIndex < target.length; targetIndex++) {
            if (source[sourceIndex + targetIndex] != target[targetIndex]) {
                return false;
            }
        }
        return true;
    }

    private static final class ExceptionUtilClassLoader extends URLClassLoader {
        private final byte[] exceptionUtilClassBytes;
        private final ProtectionDomain protectionDomain;

        private ExceptionUtilClassLoader(
                URL[] urls,
                ClassLoader parent,
                byte[] exceptionUtilClassBytes) {
            super(urls, parent);
            this.exceptionUtilClassBytes = exceptionUtilClassBytes;
            CodeSource codeSource = new CodeSource(urls[0], (Certificate[]) null);
            this.protectionDomain = new ProtectionDomain(codeSource, null, this, null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (EXCEPTION_UTIL_CLASS_NAME.equals(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        loadedClass = loadExceptionUtilClass(name);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
                return super.loadClass(name, resolve);
            }
        }

        private Class<?> loadExceptionUtilClass(String name) throws ClassNotFoundException {
            if (exceptionUtilClassBytes == null) {
                return findClass(name);
            }
            return defineClass(name, exceptionUtilClassBytes, 0, exceptionUtilClassBytes.length, protectionDomain);
        }
    }
}
