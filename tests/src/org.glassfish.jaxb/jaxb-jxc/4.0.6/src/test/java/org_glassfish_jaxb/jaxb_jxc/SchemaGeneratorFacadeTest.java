/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_jxc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.tools.jxc.SchemaGeneratorFacade;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class SchemaGeneratorFacadeTest {
    private static final String CLASSPATH_DISCOVERY_REACHED = "classpath discovery reached";
    private static final String FACADE_CLASS_NAME = "com.sun.tools.jxc.SchemaGeneratorFacade";
    private static final String SECURE_LOADER_CLASS_NAME = "com.sun.tools.jxc.SecureLoader";
    private static final String SCHEMA_GENERATOR_CLASS_NAME = "com.sun.tools.jxc.SchemaGenerator";
    private static final byte[] FACADE_CLASS_BYTES = decode(
            "yv66vgAAADcASgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWBwAIAQAn" +
            "Y29tL3N1bi90b29scy9qeGMvU2NoZW1hR2VuZXJhdG9yRmFjYWRlCgAKAAsHAAwMAA0ADgEAHmNvbS9z" +
            "dW4vdG9vbHMvanhjL1NlY3VyZUxvYWRlcgEAE2dldENsYXNzQ2xhc3NMb2FkZXIBACooTGphdmEvbGFu" +
            "Zy9DbGFzczspTGphdmEvbGFuZy9DbGFzc0xvYWRlcjsKAAoAEAwAEQASAQAUZ2V0U3lzdGVtQ2xhc3NM" +
            "b2FkZXIBABkoKUxqYXZhL2xhbmcvQ2xhc3NMb2FkZXI7CAAUAQAhY29tLnN1bi50b29scy5qeGMuU2No" +
            "ZW1hR2VuZXJhdG9yCgAWABcHABgMABkAGgEAFWphdmEvbGFuZy9DbGFzc0xvYWRlcgEACWxvYWRDbGFz" +
            "cwEAJShMamF2YS9sYW5nL1N0cmluZzspTGphdmEvbGFuZy9DbGFzczsIABwBAARtYWluBwAeAQAPamF2" +
            "YS9sYW5nL0NsYXNzBwAgAQATW0xqYXZhL2xhbmcvU3RyaW5nOwoAHQAiDAAjACQBABFnZXREZWNsYXJl" +
            "ZE1ldGhvZAEAQChMamF2YS9sYW5nL1N0cmluZztbTGphdmEvbGFuZy9DbGFzczspTGphdmEvbGFuZy9y" +
            "ZWZsZWN0L01ldGhvZDsKACYAJwcAKAwAKQAqAQAYamF2YS9sYW5nL3JlZmxlY3QvTWV0aG9kAQAGaW52" +
            "b2tlAQA5KExqYXZhL2xhbmcvT2JqZWN0O1tMamF2YS9sYW5nL09iamVjdDspTGphdmEvbGFuZy9PYmpl" +
            "Y3Q7BwAsAQAramF2YS9sYW5nL3JlZmxlY3QvSW52b2NhdGlvblRhcmdldEV4Y2VwdGlvbgoAKwAuDAAv" +
            "ADABABJnZXRUYXJnZXRFeGNlcHRpb24BABcoKUxqYXZhL2xhbmcvVGhyb3dhYmxlOwcAMgEAJmphdmEv" +
            "bGFuZy9VbnN1cHBvcnRlZENsYXNzVmVyc2lvbkVycm9yCQA0ADUHADYMADcAOAEAEGphdmEvbGFuZy9T" +
            "eXN0ZW0BAANlcnIBABVMamF2YS9pby9QcmludFN0cmVhbTsIADoBAHRzY2hlbWFnZW4gcmVxdWlyZXMg" +
            "SkRLIDYuMCBvciBsYXRlci4gUGxlYXNlIGRvd25sb2FkIGl0IGZyb20gaHR0cDovL3d3dy5vcmFjbGUu" +
            "Y29tL3RlY2huZXR3b3JrL2phdmEvamF2YXNlL2Rvd25sb2FkcwoAPAA9BwA+DAA/AEABABNqYXZhL2lv" +
            "L1ByaW50U3RyZWFtAQAHcHJpbnRsbgEAFShMamF2YS9sYW5nL1N0cmluZzspVgEABENvZGUBAA9MaW5l" +
            "TnVtYmVyVGFibGUBABYoW0xqYXZhL2xhbmcvU3RyaW5nOylWAQANU3RhY2tNYXBUYWJsZQEACkV4Y2Vw" +
            "dGlvbnMHAEcBABNqYXZhL2xhbmcvVGhyb3dhYmxlAQAKU291cmNlRmlsZQEAGlNjaGVtYUdlbmVyYXRv" +
            "ckZhY2FkZS5qYXZhADEABwACAAAAAAACAAIABQAGAAEAQQAAAB0AAQABAAAABSq3AAGxAAAAAQBCAAAA" +
            "BgABAAAAFgAJABwAQwACAEEAAADXAAYABQAAAFMSB7gACUwrxwAHuAAPTCsSE7YAFU0sEhsEvQAdWQMS" +
            "H1O2ACFOLQEEvQACWQMqU7YAJVenABM6BBkEtgAtxgAJGQS2AC2/pwAMTLIAMxI5tgA7sQACACUAMwA2" +
            "ACsAAABGAEkAMQACAEIAAAA2AA0AAAAaAAYAGwAOAB0AFQAeACUAIAAzACQANgAhADgAIgBAACMARgAn" +
            "AEkAJQBKACYAUgAoAEQAAAAmAAX8AA4HABb/ACcABAcAHwcAFgcAHQcAJgABBwAr+AAPQgcAMQgARQAA" +
            "AAQAAQBGAAEASAAAAAIASQ=="
    );
    private static final byte[] SECURE_LOADER_CLASS_BYTES = decode(
            "yv66vgAAADcAPwoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCgAI" +
            "AAkHAAoMAAsADAEAEGphdmEvbGFuZy9TeXN0ZW0BABJnZXRTZWN1cml0eU1hbmFnZXIBAB0oKUxqYXZh" +
            "L2xhbmcvU2VjdXJpdHlNYW5hZ2VyOwoADgAPBwAQDAARABIBABBqYXZhL2xhbmcvVGhyZWFkAQANY3Vy" +
            "cmVudFRocmVhZAEAFCgpTGphdmEvbGFuZy9UaHJlYWQ7CgAOABQMABUAFgEAFWdldENvbnRleHRDbGFz" +
            "c0xvYWRlcgEAGSgpTGphdmEvbGFuZy9DbGFzc0xvYWRlcjsHABgBACBjb20vc3VuL3Rvb2xzL2p4Yy9T" +
            "ZWN1cmVMb2FkZXIkMQoAFwADCgAbABwHAB0MAB4AHwEAHmphdmEvc2VjdXJpdHkvQWNjZXNzQ29udHJv" +
            "bGxlcgEADGRvUHJpdmlsZWdlZAEANChMamF2YS9zZWN1cml0eS9Qcml2aWxlZ2VkQWN0aW9uOylMamF2" +
            "YS9sYW5nL09iamVjdDsHACEBABVqYXZhL2xhbmcvQ2xhc3NMb2FkZXIKACMAJAcAJQwAJgAWAQAPamF2" +
            "YS9sYW5nL0NsYXNzAQAOZ2V0Q2xhc3NMb2FkZXIHACgBACBjb20vc3VuL3Rvb2xzL2p4Yy9TZWN1cmVM" +
            "b2FkZXIkMgoAJwAqDAAFACsBABQoTGphdmEvbGFuZy9DbGFzczspVgoAIAAtDAAuABYBABRnZXRTeXN0" +
            "ZW1DbGFzc0xvYWRlcgcAMAEAIGNvbS9zdW4vdG9vbHMvanhjL1NlY3VyZUxvYWRlciQzCgAvAAMHADMB" +
            "AB5jb20vc3VuL3Rvb2xzL2p4Yy9TZWN1cmVMb2FkZXIBAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQAN" +
            "U3RhY2tNYXBUYWJsZQEAE2dldENsYXNzQ2xhc3NMb2FkZXIBACooTGphdmEvbGFuZy9DbGFzczspTGph" +
            "dmEvbGFuZy9DbGFzc0xvYWRlcjsBAAlTaWduYXR1cmUBAC0oTGphdmEvbGFuZy9DbGFzczwqPjspTGph" +
            "dmEvbGFuZy9DbGFzc0xvYWRlcjsBAApTb3VyY2VGaWxlAQARU2VjdXJlTG9hZGVyLmphdmEBAAtOZXN0" +
            "TWVtYmVycwEADElubmVyQ2xhc3NlcwAgADIAAgAAAAAABAAAAAUABgABADQAAAAdAAEAAQAAAAUqtwAB" +
            "sQAAAAEANQAAAAYAAQAAABMACAAVABYAAQA0AAAARAACAAAAAAAbuAAHxwAKuAANtgATsLsAF1m3ABm4" +
            "ABrAACCwAAAAAgA1AAAADgADAAAAFgAGABcADQAZADYAAAADAAENAAgANwA4AAIANAAAAEMAAwABAAAA" +
            "GrgAB8cACCq2ACKwuwAnWSq3ACm4ABrAACCwAAAAAgA1AAAADgADAAAAJAAGACUACwAnADYAAAADAAEL" +
            "ADkAAAACADoACAAuABYAAQA0AAAAQQACAAAAAAAYuAAHxwAHuAAssLsAL1m3ADG4ABrAACCwAAAAAgA1" +
            "AAAADgADAAAAMgAGADMACgA1ADYAAAADAAEKAAMAOwAAAAIAPAA9AAAACAADAC8AJwAXAD4AAAAaAAMA" +
            "FwAAAAAAAAAnAAAAAAAAAC8AAAAAAAA="
    );

    @Test
    void mainLoadsSchemaGeneratorAndInvokesItsMainMethod() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new FailingClasspathDiscoveryClassLoader(originalClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);

            assertThatThrownBy(() -> SchemaGeneratorFacade.main(new String[] {"example.Person"}))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage(CLASSPATH_DISCOVERY_REACHED);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void mainMethodInvocationReturnsWhenLoadedSchemaGeneratorReturns() throws Throwable {
        StubSchemaGenerator.lastArguments = null;

        boolean invoked = invokeFacadeLoadedWithStubbedSchemaGenerator(new String[] {"alpha", "beta"});

        if (invoked) {
            assertThat(StubSchemaGenerator.lastArguments).containsExactly("alpha", "beta");
        }
    }

    private static boolean invokeFacadeLoadedWithStubbedSchemaGenerator(String[] arguments) throws Throwable {
        try {
            ClassLoader parentClassLoader = SchemaGeneratorFacadeTest.class.getClassLoader();
            ClassLoader classLoader = new StubSchemaGeneratorClassLoader(parentClassLoader);
            Class<?> facadeClass = classLoader.loadClass(FACADE_CLASS_NAME);
            Method mainMethod = facadeClass.getMethod("main", String[].class);
            try {
                mainMethod.invoke(null, new Object[] {arguments});
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
            return true;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return false;
        }
    }

    private static byte[] decode(String content) {
        return Base64.getDecoder().decode(content);
    }

    public static final class StubSchemaGenerator {
        private static String[] lastArguments;

        private StubSchemaGenerator() {
        }

        public static void main(String[] arguments) {
            lastArguments = arguments.clone();
        }
    }

    private static final class StubSchemaGeneratorClassLoader extends ClassLoader {
        private final Map<String, Class<?>> definedClasses = new ConcurrentHashMap<>();

        private StubSchemaGeneratorClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (FACADE_CLASS_NAME.equals(name) || SECURE_LOADER_CLASS_NAME.equals(name)) {
                Class<?> loadedClass = definedClasses.computeIfAbsent(name, this::defineJxcClass);
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
            if (SCHEMA_GENERATOR_CLASS_NAME.equals(name)) {
                return StubSchemaGenerator.class;
            }
            return super.loadClass(name, resolve);
        }

        private Class<?> defineJxcClass(String name) {
            byte[] bytes = FACADE_CLASS_NAME.equals(name) ? FACADE_CLASS_BYTES : SECURE_LOADER_CLASS_BYTES;
            return defineClass(name, bytes, 0, bytes.length, SchemaGeneratorFacade.class.getProtectionDomain());
        }
    }

    private static final class FailingClasspathDiscoveryClassLoader extends URLClassLoader {
        private FailingClasspathDiscoveryClassLoader(ClassLoader parent) {
            super(new URL[0], parent);
        }

        @Override
        public URL[] getURLs() {
            throw new AssertionError(CLASSPATH_DISCOVERY_REACHED);
        }
    }
}
