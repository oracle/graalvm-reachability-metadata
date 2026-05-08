/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;

public class TikaConfigInnerEncodingDetectorXmlLoaderTest {

    @Test
    public void createsCompositeWithServiceLoaderAndExcludes() throws Exception {
        String encodingDetectorXml = """
                <encodingDetector class="%s">
                  <encodingDetector-exclude class="%s"/>
                </encodingDetector>
                """.formatted(ServiceLoaderExcludesEncodingDetector.class.getName(),
                SimpleEncodingDetector.class.getName());

        TikaConfig config = configWithEncodingDetectorXml(encodingDetectorXml);

        assertThat(config.getEncodingDetector())
                .isInstanceOf(ServiceLoaderExcludesEncodingDetector.class);
        ServiceLoaderExcludesEncodingDetector encodingDetector =
                (ServiceLoaderExcludesEncodingDetector) config.getEncodingDetector();
        assertThat(encodingDetector.excludedEncodingDetectors)
                .contains(SimpleEncodingDetector.class);
    }

    @Test
    public void createsCompositeWithChildrenOnly() throws Exception {
        String encodingDetectorXml = """
                <encodingDetector class="%s">
                  <encodingDetector class="%s"/>
                </encodingDetector>
                """.formatted(ListOnlyEncodingDetector.class.getName(),
                SimpleEncodingDetector.class.getName());

        TikaConfig config = configWithEncodingDetectorXml(encodingDetectorXml);

        assertThat(config.getEncodingDetector()).isInstanceOf(ListOnlyEncodingDetector.class);
        ListOnlyEncodingDetector encodingDetector =
                (ListOnlyEncodingDetector) config.getEncodingDetector();
        assertThat(encodingDetector.childCount).isEqualTo(1);
    }

    private static TikaConfig configWithEncodingDetectorXml(String encodingDetectorXml)
            throws Exception {
        String xml = """
                <properties>
                  <encodingDetectors>
                    %s
                  </encodingDetectors>
                </properties>
                """.formatted(encodingDetectorXml);
        return new TikaConfig(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public static class ServiceLoaderExcludesEncodingDetector extends CompositeEncodingDetector {
        private static final long serialVersionUID = 1L;
        private final Collection<Class<? extends EncodingDetector>> excludedEncodingDetectors;

        public ServiceLoaderExcludesEncodingDetector(ServiceLoader loader,
                Collection<Class<? extends EncodingDetector>> excludedEncodingDetectors) {
            super(List.of(), excludedEncodingDetectors);
            assertThat(loader).isNotNull();
            this.excludedEncodingDetectors = excludedEncodingDetectors;
        }
    }

    public static class ListOnlyEncodingDetector extends CompositeEncodingDetector {
        private static final long serialVersionUID = 1L;
        private final int childCount;

        public ListOnlyEncodingDetector(List<EncodingDetector> childEncodingDetectors) {
            super(childEncodingDetectors);
            this.childCount = childEncodingDetectors.size();
        }
    }

    public static class SimpleEncodingDetector implements EncodingDetector {
        private static final long serialVersionUID = 1L;

        public SimpleEncodingDetector() {
        }

        @Override
        public Charset detect(InputStream input, Metadata metadata) throws IOException {
            return StandardCharsets.UTF_8;
        }
    }
}
