/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import org.hsqldb.util.MainInvoker;
import org.junit.jupiter.api.Test;

public class MainInvokerTest {
    private static String[] invokedArguments;

    @Test
    void invokesNamedClassMainMethodWithArguments() throws Exception {
        String[] arguments = { "first", "second" };

        MainInvoker.invoke(MainInvokerTest.class.getName(), arguments);

        assertThat(invokedArguments).containsExactly("first", "second");
    }

    public static void main(String[] arguments) {
        invokedArguments = arguments.clone();
    }
}
