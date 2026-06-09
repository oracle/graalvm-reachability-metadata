/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import io.restassured.internal.http.ContentTypeSubTypeExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContentTypeSubTypeExtractorTest {
    @Test
    void extractsQuotedSubtypeValueFromContentType() {
        String contentType = "multipart/form-data; charset=UTF-8; boundary=\"rest-assured-boundary\"";

        String boundary = ContentTypeSubTypeExtractor.getSubTypeValueFromContentType(contentType, "boundary");

        assertThat(boundary).isEqualTo("rest-assured-boundary");
    }

    @Test
    void extractsSubtypeValueCaseInsensitively() {
        String contentType = "application/json; Charset=\"ISO-8859-1\"";

        String charset = ContentTypeSubTypeExtractor.getSubTypeValueFromContentType(contentType, "charset");

        assertThat(charset).isEqualTo("ISO-8859-1");
    }

    @Test
    void returnsNullWhenSubtypeIsAbsent() {
        String contentType = "application/json; charset=UTF-8";

        String boundary = ContentTypeSubTypeExtractor.getSubTypeValueFromContentType(contentType, "boundary");

        assertThat(boundary).isNull();
    }

    @Test
    void generatedClassHelperResolvesContentTypeSubTypeExtractorClassName() throws Throwable {
        MethodHandle classHelper = MethodHandles.privateLookupIn(
                        ContentTypeSubTypeExtractor.class,
                        MethodHandles.lookup())
                .findStatic(
                        ContentTypeSubTypeExtractor.class,
                        "class$",
                        MethodType.methodType(Class.class, String.class));

        Object resolvedClass = classHelper.invoke(ContentTypeSubTypeExtractor.class.getName());

        assertThat(resolvedClass).isSameAs(ContentTypeSubTypeExtractor.class);
    }
}
