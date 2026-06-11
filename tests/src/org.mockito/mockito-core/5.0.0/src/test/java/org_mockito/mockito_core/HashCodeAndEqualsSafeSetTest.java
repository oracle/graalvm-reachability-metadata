/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class HashCodeAndEqualsSafeSetTest {
    @Test
    void mockingDetailsInvocationsCanBeCopiedToTypedArray() {
        @SuppressWarnings("unchecked")
        List<String> list = Mockito.mock(List.class);
        list.add("mockito");

        Collection<Invocation> invocations = Mockito.mockingDetails(list).getInvocations();
        Invocation[] copied = invocations.toArray(new Invocation[0]);

        assertThat(copied).hasSize(1);
        assertThat(copied[0].getMethod().getName()).isEqualTo("add");
    }
}
