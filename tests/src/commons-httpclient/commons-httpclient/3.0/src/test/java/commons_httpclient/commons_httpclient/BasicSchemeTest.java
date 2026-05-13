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
import org.apache.commons.httpclient.auth.BasicScheme;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class BasicSchemeTest {
    private static final String BASIC_SCHEME_CLASS_NAME =
            "org.apache.commons.httpclient.auth.BasicScheme";

    @Test
    @Order(2)
    void compilerGeneratedClassLookupReturnsBasicSchemeClass() throws Exception {
        Method classLookup = BasicScheme.class.getDeclaredMethod("class$", String.class);
        classLookup.setAccessible(true);

        Object resolvedClass = classLookup.invoke(null, BASIC_SCHEME_CLASS_NAME);

        assertThat(resolvedClass).isSameAs(BasicScheme.class);
    }

    @Test
    @Order(3)
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (BasicSchemeClassLoader classLoader = newBasicSchemeClassLoader()) {
            Class<?> basicSchemeClass = Class.forName(BASIC_SCHEME_CLASS_NAME, true, classLoader);

            assertThat(basicSchemeClass.getName()).isEqualTo(BASIC_SCHEME_CLASS_NAME);
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    @Order(1)
    void processChallengeStoresRealmAndCompletesAuthentication() throws Exception {
        BasicScheme scheme = new BasicScheme();

        assertThat(scheme.isComplete()).isFalse();

        scheme.processChallenge("Basic realm=\"private\"");

        assertThat(scheme.isComplete()).isTrue();
        assertThat(scheme.getSchemeName()).isEqualTo("basic");
        assertThat(scheme.getRealm()).isEqualTo("private");
        assertThat(scheme.isConnectionBased()).isFalse();
    }

    @Test
    @Order(4)
    void staticAuthenticateBuildsBasicAuthorizationHeader() {
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
                "Aladdin", "open sesame");

        String header = BasicScheme.authenticate(credentials, "ISO-8859-1");

        assertThat(header).isEqualTo("Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
    }

    private static BasicSchemeClassLoader newBasicSchemeClassLoader() {
        URL location = BasicScheme.class.getProtectionDomain().getCodeSource().getLocation();
        return new BasicSchemeClassLoader(new URL[] {location},
                BasicSchemeTest.class.getClassLoader());
    }

    private static final class BasicSchemeClassLoader extends URLClassLoader {
        private BasicSchemeClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (BASIC_SCHEME_CLASS_NAME.equals(name)) {
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
