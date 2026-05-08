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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RenderingParser;
import org.apache.tika.renderer.CompositeRenderer;
import org.apache.tika.renderer.RenderRequest;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.Renderer;

public class TikaConfigInnerRendererXmlLoaderTest {

    @Test
    public void createsCompositeWithServiceLoaderAndExcludes() throws Exception {
        String rendererXml = """
                <renderer class="%s">
                  <renderer-exclude class="%s"/>
                </renderer>
                """.formatted(ServiceLoaderExcludesRenderer.class.getName(),
                SimpleRenderer.class.getName());

        CapturingRenderingParser parser = configWithRendererXml(rendererXml);

        assertThat(parser.renderer).isInstanceOf(ServiceLoaderExcludesRenderer.class);
        ServiceLoaderExcludesRenderer renderer = (ServiceLoaderExcludesRenderer) parser.renderer;
        assertThat(renderer.excludedRenderers).contains(SimpleRenderer.class);
    }

    @Test
    public void createsCompositeWithChildrenOnly() throws Exception {
        String rendererXml = """
                <renderer class="%s">
                  <renderer class="%s"/>
                </renderer>
                """.formatted(ListOnlyRenderer.class.getName(), SimpleRenderer.class.getName());

        CapturingRenderingParser parser = configWithRendererXml(rendererXml);

        assertThat(parser.renderer).isInstanceOf(ListOnlyRenderer.class);
        ListOnlyRenderer renderer = (ListOnlyRenderer) parser.renderer;
        assertThat(renderer.childCount).isEqualTo(1);
    }

    private static CapturingRenderingParser configWithRendererXml(String rendererXml)
            throws Exception {
        String xml = """
                <properties>
                  <renderers>
                    %s
                  </renderers>
                  <parsers>
                    <parser class="%s"/>
                  </parsers>
                </properties>
                """.formatted(rendererXml, CapturingRenderingParser.class.getName());
        TikaConfig config = new TikaConfig(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Parser parser = ((CompositeParser) config.getParser()).getAllComponentParsers().get(0);
        return (CapturingRenderingParser) parser;
    }

    public static class ServiceLoaderExcludesRenderer extends CompositeRenderer {
        private static final long serialVersionUID = 1L;
        private final Collection<Class<? extends Renderer>> excludedRenderers;

        public ServiceLoaderExcludesRenderer(ServiceLoader loader,
                Collection<Class<? extends Renderer>> excludedRenderers) {
            super(List.of());
            assertThat(loader).isNotNull();
            this.excludedRenderers = excludedRenderers;
        }
    }

    public static class ListOnlyRenderer extends CompositeRenderer {
        private static final long serialVersionUID = 1L;
        private final int childCount;

        public ListOnlyRenderer(List<Renderer> childRenderers) {
            super(childRenderers);
            this.childCount = childRenderers.size();
        }
    }

    public static class SimpleRenderer implements Renderer {
        private static final long serialVersionUID = 1L;

        public SimpleRenderer() {
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(MediaType.TEXT_PLAIN);
        }

        @Override
        public RenderResults render(InputStream is, Metadata metadata, ParseContext parseContext,
                                    RenderRequest... requests) throws IOException, TikaException {
            return null;
        }
    }

    public static class CapturingRenderingParser implements Parser, RenderingParser {
        private static final long serialVersionUID = 1L;
        private Renderer renderer;

        public CapturingRenderingParser() {
        }

        @Override
        public void setRenderer(Renderer renderer) {
            this.renderer = renderer;
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
}
