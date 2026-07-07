/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.blocks.MethodCall;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodCallTest {
    @BeforeAll
    static void configureLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

    @Test
    void invokesNamedMethodOnTargetObject() throws Exception {
        InvocationTarget target = new InvocationTarget();
        MethodCall call = new MethodCall(
                "join",
                new Object[] {"node", 3},
                new Class<?>[] {String.class, int.class});

        Object result = call.invoke(target);

        assertThat(result).isEqualTo("node-3");
        assertThat(call.toString()).isEqualTo("join(node, 3)");
    }

    public static final class InvocationTarget {
        public String join(String prefix, int value) {
            return prefix + "-" + value;
        }
    }
}
