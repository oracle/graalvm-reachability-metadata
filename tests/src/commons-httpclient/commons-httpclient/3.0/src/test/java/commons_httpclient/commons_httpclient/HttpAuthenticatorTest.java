/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.auth.BasicScheme;
import org.apache.commons.httpclient.auth.CredentialsNotAvailableException;
import org.apache.commons.httpclient.auth.HttpAuthenticator;
import org.apache.commons.httpclient.auth.NTLMScheme;
import org.apache.commons.httpclient.methods.GetMethod;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class HttpAuthenticatorTest {
    private static final String HTTP_AUTHENTICATOR_CLASS_NAME =
            "org.apache.commons.httpclient.auth.HttpAuthenticator";

    @Test
    void legacyClassLiteralHelperLoadsAuthenticatorType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                HttpAuthenticator.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                HttpAuthenticator.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(HTTP_AUTHENTICATOR_CLASS_NAME);

        assertThat(resolvedClass).isSameAs(HttpAuthenticator.class);
    }

    @Test
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (HttpAuthenticatorClassLoader classLoader = newHttpAuthenticatorClassLoader()) {
            Class<?> authenticatorClass = Class.forName(
                    HTTP_AUTHENTICATOR_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(authenticatorClass.getName()).isEqualTo(HTTP_AUTHENTICATOR_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(authenticatorClass).isSameAs(HttpAuthenticator.class);
            } else {
                assertThat(authenticatorClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void selectAuthSchemeChoosesStrongestSupportedChallenge() throws Exception {
        Header[] challenges = {
            new Header(HttpAuthenticator.WWW_AUTH, "Basic realm=\"public\""),
            new Header(HttpAuthenticator.WWW_AUTH, "Digest realm=\"private\", nonce=\"abc\""),
            new Header(HttpAuthenticator.WWW_AUTH, "NTLM")
        };

        AuthScheme scheme = HttpAuthenticator.selectAuthScheme(challenges);

        assertThat(scheme).isInstanceOf(NTLMScheme.class);
        assertThat(scheme.getSchemeName()).isEqualTo("ntlm");
    }

    @Test
    void authenticateDefaultAddsBasicAuthorizationHeaderFromDefaultCredentials()
            throws Exception {
        HttpState state = new HttpState();
        state.setCredentials(null, null,
                new UsernamePasswordCredentials("Aladdin", "open sesame"));
        GetMethod method = new GetMethod("/");

        boolean authenticated = HttpAuthenticator.authenticateDefault(method, null, state);

        assertThat(authenticated).isTrue();
        assertThat(method.getRequestHeader(HttpAuthenticator.WWW_AUTH_RESP).getValue())
                .isEqualTo("Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
    }

    @Test
    void authenticateReportsMissingCredentialsForRequestedRealm() throws Exception {
        BasicScheme scheme = new BasicScheme("Basic realm=\"private\"");
        GetMethod method = new GetMethod("/");
        HttpState state = new HttpState();

        assertThatThrownBy(() -> HttpAuthenticator.authenticate(scheme, method, null, state))
                .isInstanceOf(CredentialsNotAvailableException.class)
                .hasMessageContaining("private");
    }

    private static HttpAuthenticatorClassLoader newHttpAuthenticatorClassLoader() {
        URL location = HttpAuthenticator.class.getProtectionDomain().getCodeSource().getLocation();
        return new HttpAuthenticatorClassLoader(
                new URL[] {location},
                HttpAuthenticatorTest.class.getClassLoader());
    }

    private static final class HttpAuthenticatorClassLoader extends URLClassLoader {
        private HttpAuthenticatorClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (HTTP_AUTHENTICATOR_CLASS_NAME.equals(name)) {
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
