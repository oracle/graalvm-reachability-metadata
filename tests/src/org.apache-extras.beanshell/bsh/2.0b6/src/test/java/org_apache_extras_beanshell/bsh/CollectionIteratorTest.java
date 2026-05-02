/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import bsh.collection.CollectionIterator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionIteratorTest {
    @Test
    void iteratesOverJavaIterableUsingPublicIteratorContract() {
        Path path = Path.of("alpha", "beta", "gamma");

        assertThat(path)
                .isInstanceOf(Iterable.class)
                .isNotInstanceOf(Collection.class);

        CollectionIterator iterator = new CollectionIterator(path);
        List<String> names = new ArrayList<>();
        while (iterator.hasNext()) {
            names.add(iterator.next().toString());
        }

        assertThat(names).containsExactly("alpha", "beta", "gamma");
    }
}
