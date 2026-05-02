/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_common;

import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.parquet.format.CompressionCodec;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompressionCodecNameTest {
    @Test
    public void resolvesCompressionCodecMetadataAndProbesOptionalHadoopCodecClass() {
        CompressionCodecName codecName = CompressionCodecName.fromParquet(CompressionCodec.GZIP);

        assertThat(codecName).isEqualTo(CompressionCodecName.GZIP);
        assertThat(codecName.getExtension()).isEqualTo(".gz");
        assertThat(codecName.getHadoopCompressionCodecClassName()).isEqualTo(GzipCodec.class.getName());
        assertThat(codecName.getHadoopCompressionCodecClass()).isEqualTo(GzipCodec.class);
    }
}
