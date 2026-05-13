/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class PostMethodTest {
    private static final String POST_METHOD_CLASS_NAME =
            "org.apache.commons.httpclient.methods.PostMethod";

    @Test
    @Order(1)
    void classForNameInitializesPostMethodThroughLegacyClassHelper() throws Exception {
        String postMethodClassName = new StringBuilder("org.apache.commons.httpclient.methods")
                .append(".PostMethod")
                .toString();

        Class<?> postMethodClass = Class.forName(postMethodClassName);

        assertThat(postMethodClass.getName()).isEqualTo(POST_METHOD_CLASS_NAME);
    }

    @Test
    @Order(2)
    void compilerGeneratedClassLookupResolvesPostMethodType() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                PostMethod.class,
                MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                PostMethod.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        Class<?> resolvedClass = (Class<?>) classLookup.invoke(POST_METHOD_CLASS_NAME);

        assertThat(resolvedClass).isEqualTo(PostMethod.class);
    }

    @Test
    @Order(3)
    void constructorInitializesPostMethodDefaults() throws Exception {
        PostMethod method = new PostMethod("/submit?source=test");

        assertThat(method.getName()).isEqualTo("POST");
        assertThat(method.getPath()).isEqualTo("/submit");
        assertThat(method.getQueryString()).isEqualTo("source=test");
        assertThat(method.getParameters()).isEmpty();
    }

    @Test
    @Order(4)
    void formParametersAreEncodedAsRequestEntity() throws Exception {
        PostMethod method = new PostMethod("/submit");
        method.addParameter("alpha", "one two");
        method.addParameter(new NameValuePair("symbol", "+&="));

        RequestEntity entity = method.getRequestEntity();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        entity.writeRequest(output);

        assertThat(method.getParameter("alpha").getValue()).isEqualTo("one two");
        assertThat(entity.getContentType()).isEqualTo(PostMethod.FORM_URL_ENCODED_CONTENT_TYPE);
        assertThat(output.toString(StandardCharsets.US_ASCII.name()))
                .isEqualTo("alpha=one+two&symbol=%2B%26%3D");
    }

    @Test
    @Order(5)
    void setParameterReplacesExistingValuesWithoutChangingOtherParameters() {
        PostMethod method = new PostMethod();
        method.addParameters(new NameValuePair[] {
                new NameValuePair("color", "red"),
                new NameValuePair("size", "medium"),
                new NameValuePair("color", "blue")
        });

        method.setParameter("color", "green");

        assertThat(method.getParameters())
                .extracting(NameValuePair::getName, NameValuePair::getValue)
                .containsExactly(
                        tuple("size", "medium"),
                        tuple("color", "green"));
    }

    @Test
    @Order(6)
    void removeParameterCanRemoveSpecificNameValuePair() {
        PostMethod method = new PostMethod();
        method.addParameter("mode", "draft");
        method.addParameter("mode", "published");

        boolean removed = method.removeParameter("mode", "draft");

        assertThat(removed).isTrue();
        assertThat(method.getParameters())
                .extracting(NameValuePair::getName, NameValuePair::getValue)
                .containsExactly(tuple("mode", "published"));
    }
}
