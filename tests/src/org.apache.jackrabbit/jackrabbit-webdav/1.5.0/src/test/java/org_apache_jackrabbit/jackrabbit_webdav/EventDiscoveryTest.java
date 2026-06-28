/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.observation.EventBundle;
import org.apache.jackrabbit.webdav.observation.EventDiscovery;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class EventDiscoveryTest {
    @Test
    void serializesAddedEventBundlesToObservationXml() throws Exception {
        EventDiscovery discovery = new EventDiscovery();
        assertThat(discovery.isEmpty()).isTrue();

        discovery.addEventBundle(new NamedEventBundle("first-event"));
        discovery.addEventBundle(null);
        discovery.addEventBundle(new NamedEventBundle("second-event"));

        assertThat(discovery.isEmpty()).isFalse();
        assertThat(discovery.getEventBundles()).toIterable().hasSize(2);

        Document document = newDocument();
        Element discoveryXml = discovery.toXml(document);

        assertThat(DomUtil.matches(discoveryXml, ObservationConstants.XML_EVENTDISCOVERY,
                ObservationConstants.NAMESPACE)).isTrue();
        assertThat(discoveryXml.getChildNodes().getLength()).isEqualTo(2);

        Element firstBundleXml = (Element) discoveryXml.getFirstChild();
        assertThat(DomUtil.matches(firstBundleXml, ObservationConstants.XML_EVENTBUNDLE,
                ObservationConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.getChildText(firstBundleXml, ObservationConstants.XML_EVENT,
                ObservationConstants.NAMESPACE)).isEqualTo("first-event");

        Element secondBundleXml = (Element) discoveryXml.getLastChild();
        assertThat(DomUtil.getChildText(secondBundleXml, ObservationConstants.XML_EVENT,
                ObservationConstants.NAMESPACE)).isEqualTo("second-event");
    }

    private static Document newDocument() throws Exception {
        DocumentBuilder builder = newDocumentBuilder();
        return builder.newDocument();
    }

    private static DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }

    private static final class NamedEventBundle implements EventBundle {
        private final String eventName;

        private NamedEventBundle(String eventName) {
            this.eventName = eventName;
        }

        public Element toXml(Document document) {
            Element bundle = DomUtil.createElement(document, ObservationConstants.XML_EVENTBUNDLE,
                    ObservationConstants.NAMESPACE);
            Element event = DomUtil.createElement(document, ObservationConstants.XML_EVENT,
                    ObservationConstants.NAMESPACE, eventName);
            bundle.appendChild(event);
            return bundle;
        }
    }
}
