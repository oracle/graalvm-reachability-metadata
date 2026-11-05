/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_freemarker.freemarker;

import freemarker.ext.dom.NodeModel;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import freemarker.cache.StringTemplateLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's XML DOM model to render a catalog supplied as XML.
public class XmlCatalogRenderingTest {
    @Test
    @Timeout(60)
    public void rendersXmlCatalogForStorefront() throws Exception {
        String catalogXml = "<catalog>"
                + "<product sku=\"cable\"><name>USB-C cable</name>"
                + "<price>9.50</price><tag>accessories</tag><tag>popular</tag></product>"
                + "<product sku=\"dock\"><name>Dock</name>"
                + "<price>79.00</price><tag>desk</tag></product>"
                + "</catalog>";
        NodeModel catalog = NodeModel.parse(new InputSource(new StringReader(catalogXml)));

        StringTemplateLoader templates = new StringTemplateLoader();
        templates.putTemplate("catalog.ftl", "<#list catalog[\"*\"].product as product>"
                + "${product.@sku}|${product.name}|${product.price}|"
                + "<#list product.tag as tag>${tag}<#sep>,</#list>\n"
                + "</#list>");
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setTemplateLoader(templates);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Template template = configuration.getTemplate("catalog.ftl");
        StringWriter rendered = new StringWriter();
        template.process(Map.of("catalog", catalog), rendered);

        assertEquals("cable|USB-C cable|9.50|accessories,popular\n"
                + "dock|Dock|79.00|desk\n", rendered.toString());
    }
}
