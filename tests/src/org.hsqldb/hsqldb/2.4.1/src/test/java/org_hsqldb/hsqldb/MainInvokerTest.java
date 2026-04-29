/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.hsqldb.util.MainInvoker;
import org.junit.jupiter.api.Test;

public class MainInvokerTest {
    private static String[] receivedArgs = new String[0];

    @Test
    public void invokesNamedClassMainMethodWithProvidedArguments() throws Exception {
        String[] args = {"create", "schema", "sample"};

        MainInvoker.invoke(MainInvokerTest.class.getName(), args);

        assertArrayEquals(args, receivedArgs);
    }

    @Test
    public void invokesNamedClassMainMethodWithEmptyArgumentsWhenNullIsProvided() throws Exception {
        receivedArgs = new String[]{"previous"};

        MainInvoker.invoke(MainInvokerTest.class.getName(), null);

        assertArrayEquals(new String[0], receivedArgs);
    }

    public static void main(String[] args) {
        receivedArgs = args.clone();
    }
}
