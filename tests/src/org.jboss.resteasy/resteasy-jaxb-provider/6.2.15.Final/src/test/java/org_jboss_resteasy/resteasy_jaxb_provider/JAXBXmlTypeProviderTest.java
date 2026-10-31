/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_jaxb_provider;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.namespace.QName;

import jakarta.xml.bind.JAXBElement;

import org.jboss.resteasy.plugins.providers.jaxb.JAXBXmlTypeProvider;
import org.junit.jupiter.api.Test;
import org_jboss_resteasy.resteasy_jaxb_provider.model.Item;

public class JAXBXmlTypeProviderTest {
    @Test
    void wrapsXmlTypeUsingItsPackageObjectFactory() {
        Item item = new Item("compass");

        JAXBElement<?> element = JAXBXmlTypeProvider.wrapInJAXBElement(item, Item.class);

        assertThat(element.getName()).isEqualTo(new QName("urn:inventory", "item"));
        assertThat(element.getDeclaredType()).isEqualTo(Item.class);
        assertThat(element.getValue()).isSameAs(item);
    }

    @Test
    void writesAndReadsAnXmlTypeWithoutARootElementAnnotation() throws Exception {
        try (ProviderSession session = new ProviderSession()) {
            String xml = session.write(new Item("map & compass"), Item.class, Item.class);

            assertThat(xml).contains("urn:inventory", "item", "map &amp; compass");
            Item restored = session.read(Item.class, Item.class, xml);
            assertThat(restored.name).isEqualTo("map & compass");
        }
    }
}
