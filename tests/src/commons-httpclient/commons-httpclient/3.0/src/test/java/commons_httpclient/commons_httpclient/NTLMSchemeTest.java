/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthenticationException;
import org.apache.commons.httpclient.auth.InvalidCredentialsException;
import org.apache.commons.httpclient.auth.MalformedChallengeException;
import org.apache.commons.httpclient.auth.NTLMScheme;
import org.apache.commons.httpclient.methods.GetMethod;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class NTLMSchemeTest {
    private static final String NTLM_SCHEME_CLASS_NAME =
            "org.apache.commons.httpclient.auth.NTLMScheme";

    @Test
    void compilerGeneratedClassLookupReturnsSchemeType() throws Exception {
        Method classLookup = NTLMScheme.class.getDeclaredMethod("class$", String.class);
        classLookup.setAccessible(true);

        Object resolvedClass = classLookup.invoke(null, NTLM_SCHEME_CLASS_NAME);

        assertThat(resolvedClass).isSameAs(NTLMScheme.class);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (NTLMSchemeClassLoader classLoader = newNTLMSchemeClassLoader()) {
            Class<?> ntlmSchemeClass = Class.forName(
                    NTLM_SCHEME_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(ntlmSchemeClass.getName()).isEqualTo(NTLM_SCHEME_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(ntlmSchemeClass).isSameAs(NTLMScheme.class);
            } else {
                assertThat(ntlmSchemeClass.getClassLoader()).isSameAs(classLoader);
            }

            Method classLookup = ntlmSchemeClass.getDeclaredMethod("class$", String.class);
            classLookup.setAccessible(true);
            Object resolvedClass = classLookup.invoke(null, NTLM_SCHEME_CLASS_NAME);

            assertThat(resolvedClass).isSameAs(ntlmSchemeClass);
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void processChallengeInitiatesConnectionBasedHandshake() throws Exception {
        NTLMScheme scheme = new NTLMScheme();

        assertThat(scheme.getSchemeName()).isEqualTo("ntlm");
        assertThat(scheme.getRealm()).isNull();
        assertThat(scheme.isConnectionBased()).isTrue();
        assertThat(scheme.isComplete()).isFalse();

        scheme.processChallenge("NTLM");

        assertThat(scheme.getID()).isEmpty();
        assertThat(scheme.getParameter("realm")).isNull();
    }

    @Test
    void authenticateAfterInitialChallengeCreatesTypeOneAuthorizationHeader()
            throws Exception {
        NTLMScheme scheme = new NTLMScheme("NTLM");
        NTCredentials credentials = new NTCredentials(
                "user", "password", "workstation", "domain");
        GetMethod method = new GetMethod("/");

        String header = scheme.authenticate(credentials, method);

        assertThat(header).startsWith("NTLM ");
        assertThat(header.substring("NTLM ".length())).isNotEmpty();
        assertThat(scheme.isComplete()).isFalse();
    }

    @Test
    void rejectsInvalidChallengesAndCredentials() throws Exception {
        assertThatThrownBy(() -> new NTLMScheme("Basic realm=\"private\""))
                .isInstanceOf(MalformedChallengeException.class)
                .hasMessageContaining("Invalid NTLM challenge");

        NTLMScheme scheme = new NTLMScheme("NTLM");
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
                "user", "password");

        assertThatThrownBy(() -> scheme.authenticate(credentials, new GetMethod("/")))
                .isInstanceOf(InvalidCredentialsException.class)
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining(UsernamePasswordCredentials.class.getName());
    }

    @Test
    void authenticateRequiresChallengeProcessingFirst() {
        NTLMScheme scheme = new NTLMScheme();
        NTCredentials credentials = new NTCredentials(
                "user", "password", "workstation", "domain");

        assertThatThrownBy(() -> scheme.authenticate(credentials, new GetMethod("/")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has not been initiated");
    }

    private static NTLMSchemeClassLoader newNTLMSchemeClassLoader() {
        URL location = NTLMScheme.class.getProtectionDomain().getCodeSource().getLocation();
        return new NTLMSchemeClassLoader(
                new URL[] {location},
                NTLMSchemeTest.class.getClassLoader());
    }

    private static final class NTLMSchemeClassLoader extends URLClassLoader {
        private NTLMSchemeClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (NTLM_SCHEME_CLASS_NAME.equals(name)) {
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
