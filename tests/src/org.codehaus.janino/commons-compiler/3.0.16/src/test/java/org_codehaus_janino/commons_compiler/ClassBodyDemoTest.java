/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.commons.compiler.samples.ClassBodyDemo;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ClassBodyDemoTest {
    private static final String INVOCATION_PROPERTY =
            "org_codehaus_janino.commons_compiler.ClassBodyDemoTest.invocation";

    @Test
    public void commandLineEntryPointCompilesClassBodyAndInvokesItsMainMethod() throws Exception {
        String classBody = """
                public static void main(String[] args) {
                    System.setProperty(
                        "%s",
                        args[0] + ":" + args[1] + ":" + args.length
                    );
                }
                """.formatted(INVOCATION_PROPERTY);

        System.clearProperty(INVOCATION_PROPERTY);
        try {
            ClassBodyDemo.main(new String[] { classBody, "alpha", "beta" });
            assertThat(System.getProperty(INVOCATION_PROPERTY)).isEqualTo("alpha:beta:2");
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        } finally {
            System.clearProperty(INVOCATION_PROPERTY);
        }
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
