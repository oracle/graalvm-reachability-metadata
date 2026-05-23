/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import org.hsqldb.util.MainInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MainInvokerTest {
    private static int invocationCount;
    private static String[] invokedArguments;

    @BeforeEach
    void resetInvocationState() {
        invocationCount = 0;
        invokedArguments = null;
    }

    @Test
    void invokeRunsPublicMainMethodOnNamedClassWithArguments() throws Exception {
        String[] arguments = { "alpha", "beta" };

        MainInvoker.invoke(MainInvokerTest.class.getName(), arguments);

        assertThat(invocationCount).isEqualTo(1);
        assertThat(invokedArguments).containsExactly("alpha", "beta");
    }

    @Test
    void invokeSuppliesEmptyArgumentArrayWhenArgumentsAreNull() throws Exception {
        MainInvoker.invoke(MainInvokerTest.class.getName(), null);

        assertThat(invocationCount).isEqualTo(1);
        assertThat(invokedArguments).isEmpty();
    }

    public static void main(String[] args) {
        invocationCount++;
        invokedArguments = args.clone();
    }
}
