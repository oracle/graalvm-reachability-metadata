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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.Param;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.renderer.Renderer;

public class TikaConfigInnerParserXmlLoaderTest {

    @Test
    public void createsCompositeWithRegistryLoaderExcludesEncodingDetectorAndRenderer()
            throws Exception {
        TikaConfig config = configWithParser(
                RegistryLoaderExcludesEncodingDetectorRendererParser.class.getName());

        assertThat(config.getParser())
                .isInstanceOf(RegistryLoaderExcludesEncodingDetectorRendererParser.class);
    }

    @Test
    public void createsCompositeWithRegistryLoaderExcludesAndEncodingDetector()
            throws Exception {
        TikaConfig config = configWithParser(
                RegistryLoaderExcludesEncodingDetectorParser.class.getName());

        assertThat(config.getParser())
                .isInstanceOf(RegistryLoaderExcludesEncodingDetectorParser.class);
    }

    @Test
    public void createsCompositeWithRegistryLoaderAndExcludes() throws Exception {
        TikaConfig config = configWithParser(RegistryLoaderExcludesParser.class.getName());

        assertThat(config.getParser()).isInstanceOf(RegistryLoaderExcludesParser.class);
    }

    @Test
    public void createsCompositeWithRegistryChildrenAndExcludes() throws Exception {
        TikaConfig config = configWithParser(RegistryListExcludesParser.class.getName());

        assertThat(config.getParser()).isInstanceOf(RegistryListExcludesParser.class);
    }

    @Test
    public void createsCompositeWithRegistryChildrenAndParams() throws Exception {
        String parserXml = """
                <parser class="%s">
                  <params>
                    <param name="sample" type="string">value</param>
                  </params>
                </parser>
                """.formatted(RegistryCollectionParamsParser.class.getName());

        TikaConfig config = configWithParserXml(parserXml);

        assertThat(config.getParser()).isInstanceOf(RegistryCollectionParamsParser.class);
        RegistryCollectionParamsParser parser =
                (RegistryCollectionParamsParser) config.getParser();
        assertThat(parser.params).containsKey("sample");
    }

    @Test
    public void createsCompositeWithRegistryAndChildren() throws Exception {
        TikaConfig config = configWithParser(RegistryListParser.class.getName());

        assertThat(config.getParser()).isInstanceOf(RegistryListParser.class);
    }

    @Test
    public void createsParserDecoratorAroundConfiguredChildParser() throws Exception {
        String parserXml = """
                <parser class="%s">
                  <parser class="%s"/>
                </parser>
                """.formatted(DecoratingParser.class.getName(), SimpleParser.class.getName());

        TikaConfig config = configWithParserXml(parserXml);

        Parser parser = firstComponentParser(config);
        assertThat(parser).isInstanceOf(DecoratingParser.class);
        assertThat(((DecoratingParser) parser).getWrappedParser())
                .isInstanceOf(CompositeParser.class);
    }

    @Test
    public void createsEncodingDetectorParserWithConfiguredEncodingDetector() throws Exception {
        TikaConfig config = configWithParser(EncodingDetectorAwareParser.class.getName());

        Parser parser = firstComponentParser(config);
        assertThat(parser).isInstanceOf(EncodingDetectorAwareParser.class);
        assertThat(((EncodingDetectorAwareParser) parser).getEncodingDetector()).isNotNull();
    }

    @Test
    public void createsPlainParserWithDefaultConstructor() throws Exception {
        TikaConfig config = configWithParser(SimpleParser.class.getName());

        assertThat(firstComponentParser(config)).isInstanceOf(SimpleParser.class);
    }

    private static Parser firstComponentParser(TikaConfig config) {
        return ((CompositeParser) config.getParser()).getAllComponentParsers().get(0);
    }

    private static TikaConfig configWithParser(String parserClassName) throws Exception {
        String parserXml = """
                <parser class="%s"/>
                """.formatted(parserClassName);
        return configWithParserXml(parserXml);
    }

    private static TikaConfig configWithParserXml(String parserXml) throws Exception {
        String xml = """
                <properties>
                  <parsers>
                    %s
                  </parsers>
                </properties>
                """.formatted(parserXml);
        return new TikaConfig(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public static class SimpleParser implements Parser {
        private static final long serialVersionUID = 1L;

        public SimpleParser() {
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(MediaType.TEXT_PLAIN);
        }

        @Override
        public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
        }
    }

    public static class EncodingDetectorAwareParser extends AbstractEncodingDetectorParser {
        private static final long serialVersionUID = 1L;

        public EncodingDetectorAwareParser(EncodingDetector encodingDetector) {
            super(encodingDetector);
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(MediaType.TEXT_PLAIN);
        }

        @Override
        public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
        }
    }

    public static class RegistryLoaderExcludesEncodingDetectorRendererParser
            extends CompositeParser {
        private static final long serialVersionUID = 1L;

        public RegistryLoaderExcludesEncodingDetectorRendererParser(
                MediaTypeRegistry registry, ServiceLoader loader,
                Collection<Class<? extends Parser>> excludedParsers,
                EncodingDetector encodingDetector, Renderer renderer) {
            super(registry, List.of());
            assertThat(loader).isNotNull();
            assertThat(excludedParsers).isNotNull();
            assertThat(encodingDetector).isNotNull();
            assertThat(renderer).isNotNull();
        }
    }

    public static class RegistryLoaderExcludesEncodingDetectorParser extends CompositeParser {
        private static final long serialVersionUID = 1L;

        public RegistryLoaderExcludesEncodingDetectorParser(MediaTypeRegistry registry,
                ServiceLoader loader, Collection<Class<? extends Parser>> excludedParsers,
                EncodingDetector encodingDetector) {
            super(registry, List.of());
            assertThat(loader).isNotNull();
            assertThat(excludedParsers).isNotNull();
            assertThat(encodingDetector).isNotNull();
        }
    }

    public static class RegistryLoaderExcludesParser extends CompositeParser {
        private static final long serialVersionUID = 1L;

        public RegistryLoaderExcludesParser(MediaTypeRegistry registry, ServiceLoader loader,
                Collection<Class<? extends Parser>> excludedParsers) {
            super(registry, List.of());
            assertThat(loader).isNotNull();
            assertThat(excludedParsers).isNotNull();
        }
    }

    public static class RegistryListExcludesParser extends CompositeParser {
        private static final long serialVersionUID = 1L;

        public RegistryListExcludesParser(MediaTypeRegistry registry, List<Parser> childParsers,
                Collection<Class<? extends Parser>> excludedParsers) {
            super(registry, childParsers, excludedParsers);
        }
    }

    public static class RegistryCollectionParamsParser extends CompositeParser {
        private static final long serialVersionUID = 1L;
        private final Map<String, Param> params;

        public RegistryCollectionParamsParser(MediaTypeRegistry registry,
                Collection<Parser> childParsers, Map<String, Param> params) {
            super(registry, new ArrayList<>(childParsers));
            this.params = params;
        }
    }

    public static class RegistryListParser extends CompositeParser {
        private static final long serialVersionUID = 1L;

        public RegistryListParser(MediaTypeRegistry registry, List<Parser> childParsers) {
            super(registry, childParsers);
        }
    }

    public static class DecoratingParser extends ParserDecorator {
        private static final long serialVersionUID = 1L;

        public DecoratingParser(Parser parser) {
            super(parser);
        }
    }
}
