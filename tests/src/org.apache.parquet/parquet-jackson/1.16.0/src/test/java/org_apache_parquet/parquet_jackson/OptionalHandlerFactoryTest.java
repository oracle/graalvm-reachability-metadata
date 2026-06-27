/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class OptionalHandlerFactoryTest {
    @Test
    void mapsDomDocumentsThroughJacksonPublicApi() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();

        final Document document = objectMapper.readValue(
                "\"<message><text>hello parquet</text></message>\"", Document.class);
        final String serializedDocument = objectMapper.writeValueAsString(document);

        assertThat(document.getDocumentElement().getTagName()).isEqualTo("message");
        assertThat(document.getDocumentElement().getTextContent()).isEqualTo("hello parquet");
        assertThat(serializedDocument).contains("message", "hello parquet");
    }
}
