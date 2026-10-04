/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import org.junit.jupiter.api.Test;

import org.springframework.data.couchbase.repository.support.DynamicInvocationHandler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DynamicInvocationHandlerTest {

    @Test
    void rejectsTargetsThatAreNotCouchbaseRepositories() {
        assertThatThrownBy(() -> new DynamicInvocationHandler<>(new Object(), null, null, null))
                .isInstanceOf(RuntimeException.class);
    }
}
