/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import java.util.NoSuchElementException;

import org.apache.lucene.util.SPIClassIterator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SPIClassIteratorTest {
    @Test
    void findsServiceDefinitionWithExplicitClassLoader() {
        ClassLoader classLoader = SPIClassIteratorTest.class.getClassLoader();
        SPIClassIterator<SPIClassIteratorService> iterator = SPIClassIterator.get(
                SPIClassIteratorService.class,
                classLoader);

        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isEqualTo(SPIClassIteratorServiceImplementation.class);
        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void findsServiceDefinitionWithSystemClassLoaderResources() {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        SPIClassIterator<SPIClassIteratorSystemService> iterator = SPIClassIterator.get(
                SPIClassIteratorSystemService.class,
                classLoader);

        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isEqualTo(SPIClassIteratorSystemServiceImplementation.class);
        assertThat(iterator.hasNext()).isFalse();
    }
}

interface SPIClassIteratorService {
}

class SPIClassIteratorServiceImplementation implements SPIClassIteratorService {
}

interface SPIClassIteratorSystemService {
}

class SPIClassIteratorSystemServiceImplementation implements SPIClassIteratorSystemService {
}
