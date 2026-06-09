/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.compress.BZip2Codec;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.apache.hadoop.io.compress.GzipCodec;
import org.junit.jupiter.api.Test;

public class CompressionCodecFactoryTest {
    @Test
    @SuppressWarnings("rawtypes")
    void factoryFindsCodecsByPathNameAndClassName() {
        Configuration conf = new Configuration(false);
        List<Class> codecClasses = Arrays.asList(new Class[] {
            DefaultCodec.class,
            GzipCodec.class,
            BZip2Codec.class
        });
        CompressionCodecFactory.setCodecClasses(conf, codecClasses);

        CompressionCodecFactory factory = new CompressionCodecFactory(conf);

        assertThat(factory.getCodec(new Path("dataset/part-00000.deflate")))
                .isInstanceOf(DefaultCodec.class);
        assertThat(factory.getCodec(new Path("dataset/part-00000.gz")))
                .isInstanceOf(GzipCodec.class);
        assertThat(factory.getCodecByName(GzipCodec.class.getName())).isInstanceOf(GzipCodec.class);
        assertThat(factory.getCodecByClassName(BZip2Codec.class.getName()))
                .isInstanceOf(BZip2Codec.class);
        assertThat(CompressionCodecFactory.getCodecClasses(conf))
                .extracting(Class::getName)
                .contains(
                        DefaultCodec.class.getName(),
                        GzipCodec.class.getName(),
                        BZip2Codec.class.getName());
    }

    @Test
    void suffixRemovalReturnsUnchangedNameWhenSuffixDoesNotMatch() {
        assertThat(CompressionCodecFactory.removeSuffix("part-00000.deflate", ".deflate"))
                .isEqualTo("part-00000");
        assertThat(CompressionCodecFactory.removeSuffix("part-00000.deflate", ".gz"))
                .isEqualTo("part-00000.deflate");
    }
}
