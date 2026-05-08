/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_jxc;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.tools.jxc.SchemaGenerator;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SchemaGeneratorTest {
    @Test
    void runLoadsRunnerAndInvokesCompileThroughProvidedClassLoader() throws Exception {
        StubRunner.reset();

        int exitCode = SchemaGenerator.run(
                new String[] {"-encoding", "UTF-8", "-cp", "test-classpath", "example.Person"},
                new RunnerRedirectingClassLoader());

        assertThat(exitCode).isZero();
        assertThat(StubRunner.invocationCount).isEqualTo(1);
        assertThat(StubRunner.lastEpisode).isNull();
        assertThat(StubRunner.lastArguments).isNotNull();
        List<String> arguments = Arrays.asList(StubRunner.lastArguments);
        assertThat(arguments).containsSubsequence("-encoding", "UTF-8");
        assertThat(arguments).contains("-cp", "example.Person");
    }

    public static final class StubRunner {
        private static int invocationCount;
        private static String[] lastArguments;
        private static File lastEpisode;

        private StubRunner() {
        }

        public static boolean compile(String[] arguments, File episode) {
            invocationCount++;
            lastArguments = arguments.clone();
            lastEpisode = episode;
            return true;
        }

        private static void reset() {
            invocationCount = 0;
            lastArguments = null;
            lastEpisode = null;
        }
    }

    private static final class RunnerRedirectingClassLoader extends ClassLoader {
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (SchemaGenerator.Runner.class.getName().equals(name)) {
                return StubRunner.class;
            }
            return super.loadClass(name);
        }
    }
}
