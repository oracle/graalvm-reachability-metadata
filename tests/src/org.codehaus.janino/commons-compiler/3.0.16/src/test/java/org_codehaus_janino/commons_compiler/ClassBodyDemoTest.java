/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.commons.compiler.samples.ClassBodyDemo;
import org.junit.jupiter.api.Test;

public class ClassBodyDemoTest {

    @Test
    void evaluatesAClassBodyAndInvokesItsGeneratedMainMethod() throws Exception {
        CompilerFactoryFactoryTest.EvaluatedClassBodyMain.reset();

        try (CompilerFactoryFactoryTest.ContextClassLoaderScope ignored =
                     CompilerFactoryFactoryTest.withCompilerFactoryResources(1)) {
            ClassBodyDemo.main(new String[]{
                    "public static void main(String[] args) {}",
                    "left",
                    "right"
            });
        }

        assertThat(CompilerFactoryFactoryTest.EvaluatedClassBodyMain.arguments()).containsExactly("left", "right");
        assertThat(CompilerFactoryFactoryTest.TestClassBodyEvaluator.cookedSource())
                .contains("public static void main");
    }
}
