/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContentTypeExtractorTest {
    @Test
    void contentTypeParserIgnoresCharsetParameters() {
        ContentType contentType = ContentType.fromContentType("application/json; charset=UTF-8");

        assertEquals(ContentType.JSON, contentType);
    }

    @Test
    void responseParserIgnoresCharsetParameters() {
        Parser parser = Parser.fromContentType("application/problem+json; charset=UTF-8");

        assertEquals(Parser.JSON, parser);
    }
}
