/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.ehcache.Element;
import net.sf.ehcache.store.compound.ReadWriteSerializationCopyStrategy;

import org.junit.jupiter.api.Test;

public class ReadWriteSerializationCopyStrategyTest {
    @Test
    void copyForWriteSerializesValueAndCopyForReadDeserializesIt() {
        ReadWriteSerializationCopyStrategy strategy = new ReadWriteSerializationCopyStrategy();
        Element source = new Element("copy-key", "copy-value");

        Element stored = strategy.copyForWrite(source);
        Element restored = strategy.copyForRead(stored);

        assertThat(stored).isNotSameAs(source);
        assertThat(stored.getObjectKey()).isEqualTo(source.getObjectKey());
        assertThat(stored.getObjectValue()).isInstanceOf(byte[].class);
        assertThat((byte[]) stored.getObjectValue()).isNotEmpty();
        assertThat(restored).isNotSameAs(stored);
        assertThat(restored.getObjectKey()).isEqualTo(source.getObjectKey());
        assertThat(restored.getObjectValue()).isEqualTo(source.getObjectValue());
    }
}
