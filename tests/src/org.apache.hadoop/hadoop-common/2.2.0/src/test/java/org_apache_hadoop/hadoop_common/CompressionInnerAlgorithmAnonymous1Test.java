/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.io.compress.DefaultCodec;
import org.apache.hadoop.io.file.tfile.TFile;
import org.junit.jupiter.api.Test;

public class CompressionInnerAlgorithmAnonymous1Test {
    private static final String LZO_CODEC_CLASS_PROPERTY = "io.compression.codec.lzo.class";

    @Test
    void supportedCompressionAlgorithmsProbeConfiguredLzoCodecClass() {
        String previousCodecClass = System.getProperty(LZO_CODEC_CLASS_PROPERTY);
        System.setProperty(LZO_CODEC_CLASS_PROPERTY, DefaultCodec.class.getName());
        try {
            String[] algorithms = TFile.getSupportedCompressionAlgorithms();

            assertThat(algorithms).contains(
                    TFile.COMPRESSION_LZO, TFile.COMPRESSION_GZ, TFile.COMPRESSION_NONE);
        } finally {
            restoreProperty(previousCodecClass);
        }
    }

    private static void restoreProperty(String value) {
        if (value == null) {
            System.clearProperty(LZO_CODEC_CLASS_PROPERTY);
        } else {
            System.setProperty(LZO_CODEC_CLASS_PROPERTY, value);
        }
    }
}
