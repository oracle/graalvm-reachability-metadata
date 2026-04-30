/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.namespace.QName;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class OptionalHandlerFactoryTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void usesOptionalXmlSerializerForQName() throws Exception {
        QName name = new QName("urn:parquet:test", "column", "p");

        String json = MAPPER.writeValueAsString(name);

        assertThat(json).isEqualTo("\"{urn:parquet:test}column\"");
    }
}
