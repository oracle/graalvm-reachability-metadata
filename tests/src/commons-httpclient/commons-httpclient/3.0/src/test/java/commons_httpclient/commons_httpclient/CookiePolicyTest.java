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

import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.cookie.CookieSpec;
import org.apache.commons.httpclient.cookie.CookieSpecBase;
import org.apache.commons.httpclient.cookie.IgnoreCookiesSpec;
import org.apache.commons.httpclient.cookie.NetscapeDraftSpec;
import org.apache.commons.httpclient.cookie.RFC2109Spec;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class CookiePolicyTest {
    private static final String COOKIE_POLICY_CLASS_NAME =
            "org.apache.commons.httpclient.cookie.CookiePolicy";

    @Test
    @Order(1)
    void classForNameInitializesCookiePolicy() throws Exception {
        assertThat(Class.forName("org.apache.commons.httpclient.cookie.CookieSpec").getName())
                .isEqualTo("org.apache.commons.httpclient.cookie.CookieSpec");
        assertThat(Class.forName("org.apache.commons.httpclient.cookie.CookieSpecBase").getName())
                .isEqualTo("org.apache.commons.httpclient.cookie.CookieSpecBase");
        assertThat(Class.forName("org.apache.commons.httpclient.cookie.RFC2109Spec").getName())
                .isEqualTo("org.apache.commons.httpclient.cookie.RFC2109Spec");
        assertThat(Class.forName(
                "org.apache.commons.httpclient.cookie.NetscapeDraftSpec").getName())
                .isEqualTo("org.apache.commons.httpclient.cookie.NetscapeDraftSpec");
        assertThat(Class.forName(
                "org.apache.commons.httpclient.cookie.IgnoreCookiesSpec").getName())
                .isEqualTo("org.apache.commons.httpclient.cookie.IgnoreCookiesSpec");

        Class<?> cookiePolicyClass = Class.forName(COOKIE_POLICY_CLASS_NAME);

        assertThat(cookiePolicyClass.getName()).isEqualTo(COOKIE_POLICY_CLASS_NAME);
    }

    @Test
    @Order(2)
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (CookiePolicyClassLoader classLoader = newCookiePolicyClassLoader()) {
            Class<?> cookiePolicyClass = Class.forName(COOKIE_POLICY_CLASS_NAME, true, classLoader);

            assertThat(cookiePolicyClass.getName()).isEqualTo(COOKIE_POLICY_CLASS_NAME);
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    @Order(3)
    void registeredCookieSpecsCanBeInstantiatedByPolicy() {
        CookieSpec defaultSpec = CookiePolicy.getCookieSpec(CookiePolicy.DEFAULT);
        CookieSpec rfc2109Spec = CookiePolicy.getCookieSpec(CookiePolicy.RFC_2109);
        CookieSpec browserCompatibilitySpec = CookiePolicy.getCookieSpec(
                CookiePolicy.BROWSER_COMPATIBILITY);
        CookieSpec netscapeSpec = CookiePolicy.getCookieSpec(CookiePolicy.NETSCAPE);
        CookieSpec ignoreCookiesSpec = CookiePolicy.getCookieSpec(CookiePolicy.IGNORE_COOKIES);

        assertThat(defaultSpec).isInstanceOf(RFC2109Spec.class);
        assertThat(rfc2109Spec).isInstanceOf(RFC2109Spec.class);
        assertThat(browserCompatibilitySpec).isInstanceOf(CookieSpecBase.class);
        assertThat(netscapeSpec).isInstanceOf(NetscapeDraftSpec.class);
        assertThat(ignoreCookiesSpec).isInstanceOf(IgnoreCookiesSpec.class);
    }

    private static CookiePolicyClassLoader newCookiePolicyClassLoader() {
        URL location = CookiePolicy.class.getProtectionDomain().getCodeSource().getLocation();
        return new CookiePolicyClassLoader(new URL[] {location},
                CookiePolicyTest.class.getClassLoader());
    }

    private static final class CookiePolicyClassLoader extends URLClassLoader {
        private CookiePolicyClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (COOKIE_POLICY_CLASS_NAME.equals(name)) {
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
