/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import java.util.ArrayList;
import java.util.List;
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

        assertIteratorContainsOnlyImplementation(iterator, SPIClassIteratorServiceImplementation.class);
    }

    @Test
    void findsServiceDefinitionWithSystemClassLoaderResources() {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        SPIClassIterator<SPIClassIteratorSystemService> iterator = SPIClassIterator.get(
                SPIClassIteratorSystemService.class,
                classLoader);

        assertIteratorContainsOnlyImplementation(iterator, SPIClassIteratorSystemServiceImplementation.class);
    }

    private static <T> void assertIteratorContainsOnlyImplementation(
            SPIClassIterator<T> iterator,
            Class<? extends T> expectedImplementation) {
        List<Class<? extends T>> implementations = new ArrayList<>();

        while (iterator.hasNext()) {
            implementations.add(iterator.next());
        }

        assertThat(implementations).containsOnly(expectedImplementation);
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
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
