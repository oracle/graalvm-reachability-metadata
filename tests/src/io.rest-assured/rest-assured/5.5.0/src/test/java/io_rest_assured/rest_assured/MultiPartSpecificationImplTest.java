/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.internal.multipart.MultiPartSpecificationImpl;
import io.restassured.specification.MultiPartSpecification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MultiPartSpecificationImplTest {
    @Test
    void buildsInputStreamSpecification() {
        ByteArrayInputStream content = new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8));

        MultiPartSpecification specification = new MultiPartSpecBuilder(content)
                .controlName("upload")
                .fileName("payload.txt")
                .mimeType("text/plain")
                .header("X-Part", "stream")
                .build();

        assertThat(specification).isInstanceOf(MultiPartSpecificationImpl.class);
        assertThat(specification.getContent()).isSameAs(content);
        assertThat(specification.getControlName()).isEqualTo("upload");
        assertThat(specification.getFileName()).isEqualTo("payload.txt");
        assertThat(specification.hasFileName()).isTrue();
        assertThat(specification.getMimeType()).isEqualTo("text/plain");
        assertThat(specification.getCharset()).isNull();
        assertThat(specification.getHeaders()).containsEntry("X-Part", "stream");
        assertThat(specification.toString()).contains("content=<inputstream>");
    }
}
