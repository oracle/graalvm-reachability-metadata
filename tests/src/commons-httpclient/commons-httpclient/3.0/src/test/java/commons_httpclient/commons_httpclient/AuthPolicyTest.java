/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.auth.BasicScheme;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class AuthPolicyTest {
    private static final String AUTH_POLICY_CLASS_NAME = "org.apache.commons.httpclient.auth.AuthPolicy";

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (AuthPolicyClassLoader classLoader = newAuthPolicyClassLoader()) {
            Class<?> authPolicyClass = Class.forName(AUTH_POLICY_CLASS_NAME, true, classLoader);

            assertThat(authPolicyClass.getName()).isEqualTo(AUTH_POLICY_CLASS_NAME);
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void defaultAuthSchemeCanBeInstantiatedByPolicy() {
        AuthScheme scheme = AuthPolicy.getAuthScheme(AuthPolicy.BASIC);

        assertThat(scheme).isInstanceOf(BasicScheme.class);
        assertThat(scheme.getSchemeName()).isEqualTo("basic");
    }

    @Test
    void defaultAuthPreferencesExposeBuiltInSchemesInPreferenceOrder() {
        List preferences = AuthPolicy.getDefaultAuthPrefs();

        assertThat(preferences).containsExactly("ntlm", "digest", "basic");
    }

    private static AuthPolicyClassLoader newAuthPolicyClassLoader() {
        URL location = AuthPolicy.class.getProtectionDomain().getCodeSource().getLocation();
        return new AuthPolicyClassLoader(new URL[] {location}, AuthPolicyTest.class.getClassLoader());
    }

    private static final class AuthPolicyClassLoader extends URLClassLoader {
        private AuthPolicyClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (AUTH_POLICY_CLASS_NAME.equals(name)) {
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
