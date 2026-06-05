/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderResourceAccessorTest {

    @Test
    void searchReturnsEmptyListForMissingClasspathDirectory() throws Exception {
        try (ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor()) {
            final List<Resource> resources = resourceAccessor.search(
                    "org-liquibase-liquibase-core-missing-resource-directory",
                    false
            );

            assertThat(resources).isEmpty();
        }
    }
}
