/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import co.elastic.clients.util.DuplicateResourceFinder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

public class DuplicateResourceFinderTest {
    @Test
    void ensureResourceUniquenessChecksClasspathResources() {
        DuplicateResourceFinder.enableCheck(true);

        assertThatCode(() -> DuplicateResourceFinder.ensureResourceUniqueness("missing-resource-for-uniqueness-check.txt"))
                .doesNotThrowAnyException();
    }
}
