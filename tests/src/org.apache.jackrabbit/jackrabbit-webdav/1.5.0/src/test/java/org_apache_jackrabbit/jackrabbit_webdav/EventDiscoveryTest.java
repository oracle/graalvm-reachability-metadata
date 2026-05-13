/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.observation.EventBundle;
import org.apache.jackrabbit.webdav.observation.EventDiscovery;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class EventDiscoveryTest {
    @Test
    public void addEventBundleTracksBundlesAndSerializesThemToXml() throws Exception {
        EventDiscovery discovery = new EventDiscovery();

        assertThat(discovery.isEmpty()).isTrue();

        TestEventBundle firstBundle = new TestEventBundle("first-user");
        TestEventBundle secondBundle = new TestEventBundle("second-user");
        discovery.addEventBundle(firstBundle);
        discovery.addEventBundle(null);
        discovery.addEventBundle(secondBundle);

        assertThat(discovery.isEmpty()).isFalse();
        Iterator<?> bundles = discovery.getEventBundles();
        assertThat(bundles.next()).isSameAs(firstBundle);
        assertThat(bundles.next()).isSameAs(secondBundle);
        assertThat(bundles.hasNext()).isFalse();

        Element eventDiscovery = discovery.toXml(newDocument());

        assertThat(eventDiscovery.getLocalName()).isEqualTo(ObservationConstants.XML_EVENTDISCOVERY);
        assertThat(eventDiscovery.getNamespaceURI()).isEqualTo(ObservationConstants.NAMESPACE.getURI());
        int bundleCount = eventDiscovery.getElementsByTagNameNS(
                ObservationConstants.NAMESPACE.getURI(), ObservationConstants.XML_EVENTBUNDLE).getLength();
        assertThat(bundleCount).isEqualTo(2);
        assertThat(eventDiscovery.getElementsByTagNameNS(
                ObservationConstants.NAMESPACE.getURI(), ObservationConstants.XML_EVENTUSERID).item(0).getTextContent())
                .isEqualTo("first-user");
        assertThat(eventDiscovery.getElementsByTagNameNS(
                ObservationConstants.NAMESPACE.getURI(), ObservationConstants.XML_EVENTUSERID).item(1).getTextContent())
                .isEqualTo("second-user");
    }

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }

    private static final class TestEventBundle implements EventBundle {
        private final String userId;

        private TestEventBundle(String userId) {
            this.userId = userId;
        }

        @Override
        public Element toXml(Document document) {
            Element bundle = DomUtil.createElement(
                    document, ObservationConstants.XML_EVENTBUNDLE, ObservationConstants.NAMESPACE);
            DomUtil.addChildElement(
                    bundle, ObservationConstants.XML_EVENTUSERID, ObservationConstants.NAMESPACE, userId);
            return bundle;
        }
    }
}
