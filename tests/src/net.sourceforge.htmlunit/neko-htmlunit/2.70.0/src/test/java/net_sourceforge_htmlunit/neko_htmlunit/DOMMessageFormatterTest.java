/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.neko_htmlunit;

import net.sourceforge.htmlunit.xerces.dom.DOMMessageFormatter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DOMMessageFormatterTest {

    @Test
    void initializesDomAndXmlMessageBundles() {
        DOMMessageFormatter.init();

        String domMessage = DOMMessageFormatter.formatMessage(
                DOMMessageFormatter.DOM_DOMAIN,
                "HIERARCHY_REQUEST_ERR",
                null
        );
        String xmlMessage = DOMMessageFormatter.formatMessage(
                DOMMessageFormatter.XML_DOMAIN,
                "RootElementRequired",
                null
        );

        assertThat(domMessage)
                .contains("HIERARCHY_REQUEST_ERR")
                .contains("insert a node");
        assertThat(xmlMessage)
                .contains("RootElementRequired")
                .contains("root element");
    }
}
