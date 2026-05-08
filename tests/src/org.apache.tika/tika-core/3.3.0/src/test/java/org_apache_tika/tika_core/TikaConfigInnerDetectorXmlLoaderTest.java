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
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;

public class TikaConfigInnerDetectorXmlLoaderTest {

    @Test
    public void createsCompositeWithMimeTypesLoaderAndExcludes() throws Exception {
        TikaConfig config = configWithDetector(MimeTypesLoaderExcludesDetector.class.getName());

        assertThat(config.getDetector()).isInstanceOf(MimeTypesLoaderExcludesDetector.class);
        MimeTypesLoaderExcludesDetector detector =
                (MimeTypesLoaderExcludesDetector) config.getDetector();
        assertThat(detector.excludedDetectors).isNotNull();
    }

    @Test
    public void createsCompositeWithRegistryChildrenAndExcludes() throws Exception {
        TikaConfig config = configWithDetectorXml("""
                <detector class="%s">
                  <detector class="%s"/>
                  <detector-exclude class="%s"/>
                </detector>
                """.formatted(RegistryListExcludesDetector.class.getName(),
                SimpleDetector.class.getName(), SimpleDetector.class.getName()));

        assertThat(config.getDetector()).isInstanceOf(RegistryListExcludesDetector.class);
        RegistryListExcludesDetector detector =
                (RegistryListExcludesDetector) config.getDetector();
        assertThat(detector.childCount).isEqualTo(1);
        assertThat(detector.excludedDetectors).contains(SimpleDetector.class);
    }

    @Test
    public void createsCompositeWithRegistryAndChildren() throws Exception {
        TikaConfig config = configWithDetectorXml("""
                <detector class="%s">
                  <detector class="%s"/>
                </detector>
                """.formatted(RegistryListDetector.class.getName(),
                SimpleDetector.class.getName()));

        assertThat(config.getDetector()).isInstanceOf(RegistryListDetector.class);
        RegistryListDetector detector = (RegistryListDetector) config.getDetector();
        assertThat(detector.childCount).isEqualTo(1);
    }

    @Test
    public void createsCompositeWithChildrenOnly() throws Exception {
        TikaConfig config = configWithDetectorXml("""
                <detector class="%s">
                  <detector class="%s"/>
                </detector>
                """.formatted(ListOnlyDetector.class.getName(),
                SimpleDetector.class.getName()));

        assertThat(config.getDetector()).isInstanceOf(ListOnlyDetector.class);
        ListOnlyDetector detector = (ListOnlyDetector) config.getDetector();
        assertThat(detector.childCount).isEqualTo(1);
    }

    private static TikaConfig configWithDetector(String detectorClassName) throws Exception {
        String detectorXml = """
                <detector class="%s"/>
                """.formatted(detectorClassName);
        return configWithDetectorXml(detectorXml);
    }

    private static TikaConfig configWithDetectorXml(String detectorXml) throws Exception {
        String xml = """
                <properties>
                  <detectors>
                    %s
                  </detectors>
                </properties>
                """.formatted(detectorXml);
        return new TikaConfig(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public static class MimeTypesLoaderExcludesDetector extends CompositeDetector {
        private static final long serialVersionUID = 1L;
        private final Collection<Class<? extends Detector>> excludedDetectors;

        public MimeTypesLoaderExcludesDetector(MimeTypes mimeTypes, ServiceLoader loader,
                Collection<Class<? extends Detector>> excludedDetectors) {
            super(mimeTypes.getMediaTypeRegistry(), List.of(), excludedDetectors);
            assertThat(loader).isNotNull();
            this.excludedDetectors = excludedDetectors;
        }
    }

    public static class RegistryListExcludesDetector extends CompositeDetector {
        private static final long serialVersionUID = 1L;
        private final int childCount;
        private final Collection<Class<? extends Detector>> excludedDetectors;

        public RegistryListExcludesDetector(MediaTypeRegistry registry,
                List<Detector> childDetectors,
                Collection<Class<? extends Detector>> excludedDetectors) {
            super(registry, childDetectors, excludedDetectors);
            this.childCount = childDetectors.size();
            this.excludedDetectors = excludedDetectors;
        }
    }

    public static class RegistryListDetector extends CompositeDetector {
        private static final long serialVersionUID = 1L;
        private final int childCount;

        public RegistryListDetector(MediaTypeRegistry registry, List<Detector> childDetectors) {
            super(registry, childDetectors);
            this.childCount = childDetectors.size();
        }
    }

    public static class ListOnlyDetector extends CompositeDetector {
        private static final long serialVersionUID = 1L;
        private final int childCount;

        public ListOnlyDetector(List<Detector> childDetectors) {
            super(childDetectors);
            this.childCount = childDetectors.size();
        }
    }

    public static class SimpleDetector implements Detector {
        private static final long serialVersionUID = 1L;

        public SimpleDetector() {
        }

        @Override
        public MediaType detect(InputStream input, Metadata metadata) throws IOException {
            return MediaType.TEXT_PLAIN;
        }
    }
}
