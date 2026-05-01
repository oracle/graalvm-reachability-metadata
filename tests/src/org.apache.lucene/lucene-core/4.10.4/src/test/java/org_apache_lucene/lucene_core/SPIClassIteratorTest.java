/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.util.SPIClassIterator;
import org.junit.jupiter.api.Test;

public class SPIClassIteratorTest {
    @Test
    public void discoversCodecSpiClassesWithProvidedClassLoader() {
        ClassLoader classLoader = SPIClassIteratorTest.class.getClassLoader();
        assertThat(classLoader).isNotNull();

        SPIClassIterator<Codec> iterator = SPIClassIterator.get(Codec.class, classLoader);

        assertThat(iterator.hasNext()).isTrue();
        assertCodecClass(iterator.next());
    }

    @Test
    public void discoversCodecSpiClassesWithSystemClassLoaderFallback() {
        SPIClassIterator<Codec> iterator = SPIClassIterator.get(Codec.class, null);

        assertThat(iterator.hasNext()).isTrue();
        assertCodecClass(iterator.next());
    }

    private static void assertCodecClass(Class<? extends Codec> codecClass) {
        assertThat(Codec.class.isAssignableFrom(codecClass)).isTrue();
        assertThat(codecClass.getName()).startsWith("org.apache.lucene.codecs.");
    }
}
