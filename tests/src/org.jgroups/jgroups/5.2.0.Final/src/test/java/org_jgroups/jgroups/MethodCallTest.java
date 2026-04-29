/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.BytesMessage;
import org.jgroups.blocks.MethodCall;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodCallTest {
    @Test
    void invokesResolvedMethodOnTarget() throws Exception {
        byte[] payload = new byte[] {10, 20, 30, 40};
        BytesMessage message = new BytesMessage();
        MethodCall call = new MethodCall("setArray", new Object[] {payload, 1, 2},
            new Class<?>[] {byte[].class, int.class, int.class});

        Object result = call.invoke(message);

        assertThat(result).isSameAs(message);
        assertThat(message.getArray()).isSameAs(payload);
        assertThat(message.getOffset()).isEqualTo(1);
        assertThat(message.getLength()).isEqualTo(2);
    }
}
