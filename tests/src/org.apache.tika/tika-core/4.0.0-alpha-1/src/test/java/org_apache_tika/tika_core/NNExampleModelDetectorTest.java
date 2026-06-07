/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.NNExampleModelDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

public class NNExampleModelDetectorTest {

    @Test
    public void loadsBundledModelFromClassLoaderResource() throws Exception {
        NNExampleModelDetector detector = new NNExampleModelDetector();

        detector.loadDefaultModels(NNExampleModelDetectorTest.class.getClassLoader());

        try (TikaInputStream stream = TikaInputStream.get(
                new ByteArrayInputStream("sample content".getBytes(UTF_8)))) {
            MediaType detectedType = detector.detect(stream, new Metadata(), new ParseContext());

            assertThat(detectedType).isNotNull();
        }
    }
}
