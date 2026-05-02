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
    void cloneCreatesSerializableCopy() throws CloneNotSupportedException {
        Element element = new Element("copy-key", "copy-value", 7L);
        element.updateAccessStatistics();

        Element cloned = (Element) element.clone();

        assertThat(cloned).isNotSameAs(element);
        assertThat(cloned.getObjectKey()).isEqualTo(element.getObjectKey());
        assertThat(cloned.getObjectValue()).isEqualTo(element.getObjectValue());
        assertThat(cloned.getVersion()).isEqualTo(element.getVersion());
        assertThat(cloned.getHitCount()).isEqualTo(element.getHitCount());
        assertThat(cloned.getElementEvictionData()).isNotSameAs(element.getElementEvictionData());
    }

    @Test
    void getSerializedSizeSerializesElement() {
        Element element = new Element("serialized-key", "serialized-value");
        element.setTimeToLive(60);
        element.setTimeToIdle(30);

        long serializedSize = element.getSerializedSize();

        assertThat(serializedSize).isGreaterThan(0L);
    }
}
