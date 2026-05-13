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

import org.apache.commons.httpclient.HttpMethodBase;
import org.junit.jupiter.api.Test;

public class HttpMethodBaseTest {
    private static final String HTTPCLIENT_PACKAGE_NAME = "org.apache.commons.httpclient.";

    @Test
    void legacyClassLiteralHelperLoadsRequestedType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                HttpMethodBase.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                HttpMethodBase.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        String valuePairClassName = new StringBuilder(HTTPCLIENT_PACKAGE_NAME)
                .append("NameValuePair")
                .toString();

        Class<?> resolvedClass = (Class<?>) classLookup.invoke(valuePairClassName);

        assertThat(resolvedClass.getName()).isEqualTo(valuePairClassName);
    }

    @Test
    void constructorParsesUriIntoPublicMethodState() throws Exception {
        SampleHttpMethod method = new SampleHttpMethod(
                "http://example.com:8080/a%20path?name=value");

        assertThat(method).isInstanceOf(HttpMethodBase.class);
        assertThat(method.getName()).isEqualTo("SAMPLE");
        assertThat(method.getPath()).isEqualTo("/a%20path");
        assertThat(method.getQueryString()).isEqualTo("name=value");
        assertThat(method.getURI().toString())
                .isEqualTo("http://example.com:8080/a%20path?name=value");
    }

    private static final class SampleHttpMethod extends HttpMethodBase {
        private SampleHttpMethod(String uri) {
            super(uri);
        }

        @Override
        public String getName() {
            return "SAMPLE";
        }
    }
}
