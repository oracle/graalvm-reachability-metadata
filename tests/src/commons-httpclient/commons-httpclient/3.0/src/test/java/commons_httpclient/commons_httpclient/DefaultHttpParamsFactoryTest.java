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

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.params.DefaultHttpParamsFactory;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.params.HttpParams;
import org.apache.commons.httpclient.params.HttpParamsFactory;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class DefaultHttpParamsFactoryTest {
    private static final String DEFAULT_HTTP_PARAMS_FACTORY_CLASS_NAME =
            "org.apache.commons.httpclient.params.DefaultHttpParamsFactory";
    private static final String USER_AGENT_PROPERTY = "httpclient.useragent";
    private static final String PREEMPTIVE_AUTH_PROPERTY = "httpclient.authentication.preemptive";
    private static final String COOKIE_SPEC_PROPERTY = "apache.commons.httpclient.cookiespec";

    private String userAgentProperty;
    private String preemptiveAuthProperty;
    private String cookieSpecProperty;

    @BeforeEach
    void clearLegacyProperties() {
        userAgentProperty = System.getProperty(USER_AGENT_PROPERTY);
        preemptiveAuthProperty = System.getProperty(PREEMPTIVE_AUTH_PROPERTY);
        cookieSpecProperty = System.getProperty(COOKIE_SPEC_PROPERTY);
        System.clearProperty(USER_AGENT_PROPERTY);
        System.clearProperty(PREEMPTIVE_AUTH_PROPERTY);
        System.clearProperty(COOKIE_SPEC_PROPERTY);
    }

    @AfterEach
    void restoreLegacyProperties() {
        restoreProperty(USER_AGENT_PROPERTY, userAgentProperty);
        restoreProperty(PREEMPTIVE_AUTH_PROPERTY, preemptiveAuthProperty);
        restoreProperty(COOKIE_SPEC_PROPERTY, cookieSpecProperty);
    }

    @Test
    void freshClassLoaderGetDefaultParamsRunsLegacyClassHelper() throws Exception {
        try (DefaultHttpParamsFactoryClassLoader classLoader =
                newDefaultHttpParamsFactoryClassLoader()) {
            Class<?> factoryClass = Class.forName(
                    DEFAULT_HTTP_PARAMS_FACTORY_CLASS_NAME,
                    true,
                    classLoader);
            HttpParamsFactory factory = (HttpParamsFactory) factoryClass.getDeclaredConstructor()
                    .newInstance();

            HttpParams defaults = factory.getDefaultParams();

            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(factoryClass).isSameAs(DefaultHttpParamsFactory.class);
            } else {
                assertThat(factoryClass.getClassLoader()).isSameAs(classLoader);
            }
            assertDefaultClientParams(defaults);
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    void getDefaultParamsCreatesHttpClientDefaults() {
        DefaultHttpParamsFactory factory = new DefaultHttpParamsFactory();

        HttpParams defaults = factory.getDefaultParams();

        assertDefaultClientParams(defaults);
    }

    @Test
    void getDefaultParamsCachesCreatedParams() {
        DefaultHttpParamsFactory factory = new DefaultHttpParamsFactory();

        HttpParams defaults = factory.getDefaultParams();

        assertThat(factory.getDefaultParams()).isSameAs(defaults);
    }

    private static void assertDefaultClientParams(HttpParams defaults) {
        assertThat(defaults).isInstanceOf(HttpClientParams.class);
        HttpClientParams clientParams = (HttpClientParams) defaults;
        String userAgent = (String) clientParams.getParameter(HttpMethodParams.USER_AGENT);
        assertThat(userAgent).startsWith("Jakarta Commons-HttpClient/");
        assertThat(clientParams.getVersion()).isEqualTo(HttpVersion.HTTP_1_1);
        assertThat(clientParams.getConnectionManagerClass())
                .isEqualTo(SimpleHttpConnectionManager.class);
        assertThat(clientParams.getCookiePolicy()).isEqualTo(CookiePolicy.RFC_2109);
        assertThat(clientParams.getHttpElementCharset()).isEqualTo("US-ASCII");
        assertThat(clientParams.getContentCharset()).isEqualTo("ISO-8859-1");
        assertThat(clientParams.getParameter(HttpMethodParams.RETRY_HANDLER))
                .isInstanceOf(DefaultHttpMethodRetryHandler.class);
        assertThat((List<?>) clientParams.getParameter(HttpMethodParams.DATE_PATTERNS)).hasSize(14);
    }

    private static DefaultHttpParamsFactoryClassLoader newDefaultHttpParamsFactoryClassLoader() {
        URL location = DefaultHttpParamsFactory.class.getProtectionDomain()
                .getCodeSource().getLocation();
        return new DefaultHttpParamsFactoryClassLoader(new URL[] {location},
                DefaultHttpParamsFactoryTest.class.getClassLoader());
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static final class DefaultHttpParamsFactoryClassLoader extends URLClassLoader {
        private DefaultHttpParamsFactoryClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (DEFAULT_HTTP_PARAMS_FACTORY_CLASS_NAME.equals(name)) {
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
