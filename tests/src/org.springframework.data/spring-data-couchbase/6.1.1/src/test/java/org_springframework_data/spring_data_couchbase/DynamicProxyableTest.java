/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicProxyableTest {

    @Test
    void createsOptionsAndCollectionProxiesFromRepositoryImplementation() {
        DynamicInvocationHandlerTest.TestRepository repository = DynamicInvocationHandlerTest.repository();

        DynamicInvocationHandlerTest.TestRepository withOptions = repository.withOptions(null);
        DynamicInvocationHandlerTest.TestRepository withCollection = repository.withCollection("orders");

        assertThat(withOptions).isNotSameAs(repository);
        assertThat(withCollection).isNotSameAs(repository);
    }
}
