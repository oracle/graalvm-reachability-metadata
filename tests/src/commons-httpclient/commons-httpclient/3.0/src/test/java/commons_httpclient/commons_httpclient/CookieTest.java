/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Date;

import org.apache.commons.httpclient.Cookie;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class CookieTest {
    private static final String COOKIE_CLASS_NAME = "org.apache.commons.httpclient.Cookie";

    @Test
    @Order(1)
    void classForNameInitializesCookie() throws Exception {
        Class<?> cookieClass = Class.forName(COOKIE_CLASS_NAME);

        assertThat(cookieClass.getName()).isEqualTo(COOKIE_CLASS_NAME);
    }

    @Test
    @Order(2)
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (CookieClassLoader classLoader = newCookieClassLoader()) {
            Class<?> cookieClass = Class.forName(COOKIE_CLASS_NAME, true, classLoader);

            assertThat(cookieClass.getName()).isEqualTo(COOKIE_CLASS_NAME);
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    @Order(3)
    void constructorInitializesCookieAndNormalizesDomain() {
        Date expires = new Date(System.currentTimeMillis() + 60_000L);

        Cookie cookie = new Cookie("Example.COM:8080", "session", "abc123", "/app", expires, true);
        cookie.setComment("user session");
        cookie.setVersion(1);
        cookie.setDomainAttributeSpecified(true);
        cookie.setPathAttributeSpecified(true);

        assertThat(cookie.getName()).isEqualTo("session");
        assertThat(cookie.getValue()).isEqualTo("abc123");
        assertThat(cookie.getDomain()).isEqualTo("example.com");
        assertThat(cookie.getPath()).isEqualTo("/app");
        assertThat(cookie.getExpiryDate()).isSameAs(expires);
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getComment()).isEqualTo("user session");
        assertThat(cookie.getVersion()).isEqualTo(1);
        assertThat(cookie.isPersistent()).isTrue();
        assertThat(cookie.isExpired(new Date(expires.getTime() - 1_000L))).isFalse();
        assertThat(cookie.isDomainAttributeSpecified()).isTrue();
        assertThat(cookie.isPathAttributeSpecified()).isTrue();
    }

    @Test
    @Order(4)
    void constructorRejectsInvalidCookieNames() {
        assertThatThrownBy(() -> new Cookie("example.com", null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cookie name may not be null");
        assertThatThrownBy(() -> new Cookie("example.com", "   ", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cookie name may not be blank");
        assertThatThrownBy(() -> new Cookie("example.com", "id", "value", "/", -2, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid max age:  -2");
    }

    @Test
    @Order(5)
    void comparatorOrdersMoreSpecificCookiePathsFirst() {
        Cookie comparator = new Cookie("example.com", "comparator", "value");
        Cookie rootCookie = new Cookie("example.com", "id", "root", "/", null, false);
        Cookie appCookie = new Cookie("example.com", "id", "app", "/app", null, false);
        Cookie nestedCookie = new Cookie("example.com", "id", "nested", "/app/nested", null, false);

        assertThat(comparator.compare(nestedCookie, appCookie)).isGreaterThan(0);
        assertThat(comparator.compare(appCookie, rootCookie)).isGreaterThan(0);
        assertThat(comparator.compare(rootCookie, new Cookie("example.com", "id", "missing-path")))
                .isEqualTo(0);
    }

    @Test
    @Order(6)
    void equalityUsesNameDomainAndPath() {
        Cookie first = new Cookie("Example.COM", "session", "abc123", "/app", null, false);
        Cookie sameIdentity = new Cookie("example.com", "session", "different", "/app", null, true);
        Cookie differentPath = new Cookie("example.com", "session", "abc123", "/admin", null, false);

        assertThat(first).isEqualTo(sameIdentity);
        assertThat(first).hasSameHashCodeAs(sameIdentity);
        assertThat(first).isNotEqualTo(differentPath);
    }

    private static CookieClassLoader newCookieClassLoader() {
        URL location = Cookie.class.getProtectionDomain().getCodeSource().getLocation();
        return new CookieClassLoader(new URL[] {location}, CookieTest.class.getClassLoader());
    }

    private static final class CookieClassLoader extends URLClassLoader {
        private CookieClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (COOKIE_CLASS_NAME.equals(name)) {
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
