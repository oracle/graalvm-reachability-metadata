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

import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.params.DefaultHttpParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class HttpMethodParamsTest {
    private static final String HTTP_METHOD_PARAMS_CLASS_NAME =
            "org.apache.commons.httpclient.params.HttpMethodParams";

    @Test
    @Order(1)
    void classForNameInitializesHttpMethodParams() throws Exception {
        Class<?> paramsClass = Class.forName(HTTP_METHOD_PARAMS_CLASS_NAME);

        assertThat(paramsClass).isSameAs(HttpMethodParams.class);
    }

    @Test
    @Order(2)
    void legacyClassLiteralHelperLoadsHttpMethodParamsType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                HttpMethodParams.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                HttpMethodParams.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(HTTP_METHOD_PARAMS_CLASS_NAME);
        Class<?> resolvedJdkClass = (Class<?>) classLookup.invoke("java.lang.String");

        assertThat(resolvedClass).isSameAs(HttpMethodParams.class);
        assertThat(resolvedJdkClass).isSameAs(String.class);
    }

    @Test
    @Order(3)
    void legacyClassLiteralHelperWrapsMissingClassFailures() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                HttpMethodParams.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                HttpMethodParams.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        assertThatThrownBy(() -> classLookup.invoke("example.missing.HttpMethodParams"))
                .isInstanceOf(NoClassDefFoundError.class)
                .hasMessageContaining("example.missing.HttpMethodParams");
    }

    @Test
    @Order(4)
    void freshClassLoaderInitializationRunsLegacyClassHelper() throws Exception {
        try (HttpMethodParamsClassLoader classLoader = newHttpMethodParamsClassLoader()) {
            Class<?> paramsClass = Class.forName(
                    HTTP_METHOD_PARAMS_CLASS_NAME,
                    true,
                    classLoader);

            assertThat(paramsClass.getName()).isEqualTo(HTTP_METHOD_PARAMS_CLASS_NAME);
            if (NativeImageSupport.isNativeImageRuntime()) {
                assertThat(paramsClass).isSameAs(HttpMethodParams.class);
            } else {
                assertThat(paramsClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Throwable throwable) {
            if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
                throw throwable;
            }
        }
    }

    @Test
    @Order(5)
    void constructorInitializesDefaultsAndTypedParameterAccessors() {
        DefaultHttpParams defaults = new DefaultHttpParams(null);
        defaults.setParameter(HttpMethodParams.HTTP_ELEMENT_CHARSET, "UTF-8");
        defaults.setParameter(HttpMethodParams.HTTP_CONTENT_CHARSET, "UTF-16");
        defaults.setParameter(HttpMethodParams.CREDENTIAL_CHARSET, "ISO-8859-2");
        defaults.setParameter(HttpMethodParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_0);
        defaults.setParameter(HttpMethodParams.COOKIE_POLICY, CookiePolicy.RFC_2109);
        defaults.setIntParameter(HttpMethodParams.SO_TIMEOUT, 1234);
        defaults.setParameter(HttpMethodParams.VIRTUAL_HOST, "virtual.example.test");

        HttpMethodParams params = new HttpMethodParams(defaults);

        assertThat(params.getDefaults()).isSameAs(defaults);
        assertThat(params.getHttpElementCharset()).isEqualTo("UTF-8");
        assertThat(params.getContentCharset()).isEqualTo("UTF-16");
        assertThat(params.getCredentialCharset()).isEqualTo("ISO-8859-2");
        assertThat(params.getVersion()).isEqualTo(HttpVersion.HTTP_1_0);
        assertThat(params.getCookiePolicy()).isEqualTo(CookiePolicy.RFC_2109);
        assertThat(params.getSoTimeout()).isEqualTo(1234);
        assertThat(params.getVirtualHost()).isEqualTo("virtual.example.test");
    }

    @Test
    @Order(6)
    void strictAndLenientModesUpdateProtocolStrictnessParameters() {
        HttpMethodParams params = new HttpMethodParams(null);

        params.makeStrict();

        assertThat(params.getParameter(HttpMethodParams.UNAMBIGUOUS_STATUS_LINE))
                .isEqualTo(Boolean.TRUE);
        assertThat(params.getParameter(HttpMethodParams.SINGLE_COOKIE_HEADER))
                .isEqualTo(Boolean.TRUE);
        assertThat(params.getParameter(HttpMethodParams.STRICT_TRANSFER_ENCODING))
                .isEqualTo(Boolean.TRUE);
        assertThat(params.getParameter(HttpMethodParams.REJECT_HEAD_BODY))
                .isEqualTo(Boolean.TRUE);
        assertThat(params.getParameter(HttpMethodParams.WARN_EXTRA_INPUT))
                .isEqualTo(Boolean.TRUE);
        assertThat(params.getIntParameter(HttpMethodParams.STATUS_LINE_GARBAGE_LIMIT, -1))
                .isZero();

        params.makeLenient();

        assertThat(params.getParameter(HttpMethodParams.UNAMBIGUOUS_STATUS_LINE))
                .isEqualTo(Boolean.FALSE);
        assertThat(params.getParameter(HttpMethodParams.SINGLE_COOKIE_HEADER))
                .isEqualTo(Boolean.FALSE);
        assertThat(params.getParameter(HttpMethodParams.STRICT_TRANSFER_ENCODING))
                .isEqualTo(Boolean.FALSE);
        assertThat(params.getParameter(HttpMethodParams.REJECT_HEAD_BODY))
                .isEqualTo(Boolean.FALSE);
        assertThat(params.getParameter(HttpMethodParams.WARN_EXTRA_INPUT))
                .isEqualTo(Boolean.FALSE);
        assertThat(params.getIntParameter(HttpMethodParams.STATUS_LINE_GARBAGE_LIMIT, -1))
                .isEqualTo(Integer.MAX_VALUE);
    }

    private static HttpMethodParamsClassLoader newHttpMethodParamsClassLoader() {
        URL location = HttpMethodParams.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
        return new HttpMethodParamsClassLoader(
                new URL[] {location},
                HttpMethodParamsTest.class.getClassLoader());
    }

    private static final class HttpMethodParamsClassLoader extends URLClassLoader {
        private HttpMethodParamsClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (HTTP_METHOD_PARAMS_CLASS_NAME.equals(name)) {
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
