/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.blocks.MethodCall;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodCallTest {
    @Test
    void invokesNamedMethodOnTargetObject() throws Exception {
        MethodCall call = new MethodCall("formatValue", new Object[] {"node", 3},
                new Class<?>[] {String.class, int.class});

        Object result = call.invoke(new InvocationTarget());

        assertThat(result).isEqualTo("node:3");
    }

    public static class InvocationTarget {
        public String formatValue(String value, int count) {
            return value + ':' + count;
        }
    }
}
