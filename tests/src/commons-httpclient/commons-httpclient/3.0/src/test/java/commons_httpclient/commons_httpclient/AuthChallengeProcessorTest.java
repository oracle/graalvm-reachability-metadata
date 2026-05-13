/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.auth.AuthChallengeProcessor;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.auth.AuthState;
import org.apache.commons.httpclient.auth.BasicScheme;
import org.apache.commons.httpclient.params.DefaultHttpParams;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class AuthChallengeProcessorTest {
    private static final String AUTH_CHALLENGE_PROCESSOR_CLASS_NAME =
            "org.apache.commons.httpclient.auth.AuthChallengeProcessor";

    @Test
    void compilerGeneratedClassLookupResolvesProcessorType() throws Throwable {
        Class<?> processorClass = Class.forName(
                AUTH_CHALLENGE_PROCESSOR_CLASS_NAME,
                false,
                AuthChallengeProcessor.class.getClassLoader());
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                processorClass,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                processorClass,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(
                AUTH_CHALLENGE_PROCESSOR_CLASS_NAME);

        assertThat(resolvedClass.getName()).isEqualTo(AUTH_CHALLENGE_PROCESSOR_CLASS_NAME);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (AuthChallengeProcessorClassLoader classLoader =
                newAuthChallengeProcessorClassLoader()) {
            Class<?> processorClass = Class.forName(
                    AUTH_CHALLENGE_PROCESSOR_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(processorClass.getName()).isEqualTo(AUTH_CHALLENGE_PROCESSOR_CLASS_NAME);
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void processChallengeSelectsConfiguredSchemeAndStoresChallengeState() throws Exception {
        DefaultHttpParams params = new DefaultHttpParams(null);
        params.setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY,
                Collections.singletonList(AuthPolicy.BASIC));
        AuthChallengeProcessor processor = new AuthChallengeProcessor(params);
        AuthState state = new AuthState();
        Map challenges = new HashMap();
        challenges.put("basic", "Basic realm=\"test-realm\"");

        AuthScheme scheme = processor.processChallenge(state, challenges);

        assertThat(scheme).isSameAs(state.getAuthScheme());
        assertThat(scheme).isInstanceOf(BasicScheme.class);
        assertThat(scheme.getSchemeName()).isEqualTo("basic");
        assertThat(scheme.getRealm()).isEqualTo("test-realm");
    }

    private static AuthChallengeProcessorClassLoader newAuthChallengeProcessorClassLoader() {
        URL location = AuthChallengeProcessor.class.getProtectionDomain()
                .getCodeSource().getLocation();
        return new AuthChallengeProcessorClassLoader(new URL[] {location},
                AuthChallengeProcessorTest.class.getClassLoader());
    }

    private static final class AuthChallengeProcessorClassLoader extends URLClassLoader {
        private AuthChallengeProcessorClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (AUTH_CHALLENGE_PROCESSOR_CLASS_NAME.equals(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        loadedClass = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
                return super.loadClass(name, resolve);
            }
        }
    }
}
