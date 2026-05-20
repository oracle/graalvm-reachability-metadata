/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.util.IOUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IOUtilsAndNamedSPILoaderTest {
    @Test
    void readsClasspathResourceWithExplicitCharsetDecoder() throws Exception {
        try (Reader reader = IOUtils.getDecodingReader(IOUtils.class, "/META-INF/LICENSE.txt", StandardCharsets.UTF_8)) {
            char[] buffer = new char[192];
            int read = reader.read(buffer);

            assertThat(read).isPositive();
            assertThat(new String(buffer, 0, read)).contains("Apache License");
        }
    }

    @Test
    void loadsBuiltInPostingsFormatsThroughNamedSpiLookup() {
        PostingsFormat postingsFormat = PostingsFormat.forName("Lucene50");

        assertThat(postingsFormat.getClass().getName())
                .isEqualTo("org.apache.lucene.codecs.lucene50.Lucene50PostingsFormat");
        assertThat(PostingsFormat.availablePostingsFormats()).contains("Lucene50");
    }

    @Test
    void loadsBuiltInCodecsAndDocValuesFormatsThroughNamedSpiLookup() {
        Codec codec = Codec.forName("Lucene53");
        DocValuesFormat docValuesFormat = DocValuesFormat.forName("Lucene50");

        assertThat(codec.getClass().getName()).isEqualTo("org.apache.lucene.codecs.lucene53.Lucene53Codec");
        assertThat(Codec.availableCodecs()).contains("Lucene53");
        assertThat(docValuesFormat.getClass().getName())
                .isEqualTo("org.apache.lucene.codecs.lucene50.Lucene50DocValuesFormat");
        assertThat(DocValuesFormat.availableDocValuesFormats()).contains("Lucene50");
    }
}
