/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.ehcache.Element;

import org.junit.jupiter.api.Test;

public class ElementTest {
    @Test
    void cloneUsesSerializationToCopySerializableKeyAndValue() throws CloneNotSupportedException {
        Element element = new Element("sample-key", "sample-value", 42L);

        Element cloned = (Element) element.clone();

        assertThat(cloned).isNotSameAs(element);
        assertThat(cloned.getObjectKey()).isEqualTo("sample-key");
        assertThat(cloned.getObjectValue()).isEqualTo("sample-value");
        assertThat(cloned.getVersion()).isEqualTo(42L);
    }

    @Test
    void getSerializedSizeMeasuresSerializableElement() {
        Element element = new Element("size-key", "size-value", 7L);

        assertThat(element.getSerializedSize()).isGreaterThan(0L);
    }
}
