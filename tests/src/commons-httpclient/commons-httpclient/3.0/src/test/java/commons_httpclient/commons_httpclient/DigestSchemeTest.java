/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.DigestScheme;
import org.apache.commons.httpclient.methods.GetMethod;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class DigestSchemeTest {
    private static final String DIGEST_SCHEME_CLASS_NAME =
            "org.apache.commons.httpclient.auth.DigestScheme";

    @Test
    void compilerGeneratedClassLookupReturnsDigestSchemeClass() throws Exception {
        Method classLookup = DigestScheme.class.getDeclaredMethod("class$", String.class);
        classLookup.setAccessible(true);

        Object resolvedClass = classLookup.invoke(null, DIGEST_SCHEME_CLASS_NAME);

        assertThat(resolvedClass).isSameAs(DigestScheme.class);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (DigestSchemeClassLoader classLoader = newDigestSchemeClassLoader()) {
            Class<?> digestSchemeClass = Class.forName(DIGEST_SCHEME_CLASS_NAME, true, classLoader);

            assertThat(digestSchemeClass.getName()).isEqualTo(DIGEST_SCHEME_CLASS_NAME);
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void processChallengeStoresDigestParametersAndCompletesAuthentication() throws Exception {
        DigestScheme scheme = new DigestScheme();

        assertThat(scheme.isComplete()).isFalse();

        scheme.processChallenge("Digest realm=\"testrealm@host.com\", "
                + "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", "
                + "opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"");

        assertThat(scheme.isComplete()).isTrue();
        assertThat(scheme.getSchemeName()).isEqualTo("digest");
        assertThat(scheme.getRealm()).isEqualTo("testrealm@host.com");
        assertThat(scheme.getParameter("nonce"))
                .isEqualTo("dcd98b7102dd2f0e8b11d0f600bfb0c093");
        assertThat(scheme.getParameter("opaque"))
                .isEqualTo("5ccc069c403ebaf9f0171e9517f40e41");
        assertThat(scheme.isConnectionBased()).isFalse();
    }

    @Test
    void authenticateCreatesDigestAuthorizationHeader() throws Exception {
        DigestScheme scheme = new DigestScheme();
        scheme.processChallenge("Digest realm=\"testrealm@host.com\", "
                + "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", "
                + "opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"");
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
                "Mufasa", "Circle Of Life");

        GetMethod method = new GetMethod("/dir/index.html");

        String header = scheme.authenticate(credentials, method);

        assertThat(header).isEqualTo("Digest username=\"Mufasa\", "
                + "realm=\"testrealm@host.com\", "
                + "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", "
                + "uri=\"/dir/index.html\", "
                + "response=\"670fd8c2df070c60b045671b8b24ff02\", "
                + "opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"");
    }

    private static DigestSchemeClassLoader newDigestSchemeClassLoader() {
        URL location = DigestScheme.class.getProtectionDomain().getCodeSource().getLocation();
        return new DigestSchemeClassLoader(new URL[] {location},
                DigestSchemeTest.class.getClassLoader());
    }

    private static final class DigestSchemeClassLoader extends URLClassLoader {
        private DigestSchemeClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (DIGEST_SCHEME_CLASS_NAME.equals(name)) {
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
