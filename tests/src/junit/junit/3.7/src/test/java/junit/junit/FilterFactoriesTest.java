/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.FilterFactory;
import org.junit.runner.FilterFactoryParams;
import org.junit.runner.JUnitCore;
import org.junit.runner.manipulation.Filter;

public class FilterFactoriesTest {
    private static final String ALLOWED_OUTPUT = "FilterFactoriesTest allowed target ran";
    private static final String REJECTED_OUTPUT = "FilterFactoriesTest rejected target ran";

    @Test
    void commandLineFilterCreatesFilterFactoryFromClassName() throws Exception {
        Process process = new ProcessBuilder(junitCoreCommand())
                .redirectErrorStream(true)
                .start();

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(finished).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(output).contains(ALLOWED_OUTPUT);
        assertThat(output).doesNotContain(REJECTED_OUTPUT);
    }

    public static final class NamePrefixFilterFactory implements FilterFactory {
        public NamePrefixFilterFactory() {
        }

        @Override
        public Filter createFilter(FilterFactoryParams params) {
            String prefix = params.getArgs();
            return new Filter() {
                @Override
                public boolean shouldRun(Description description) {
                    if (description.isSuite()) {
                        return true;
                    }
                    String methodName = description.getMethodName();
                    return methodName != null && methodName.startsWith(prefix);
                }

                @Override
                public String describe() {
                    return "method name starts with " + prefix;
                }
            };
        }
    }

    public static final class FilterTarget {
        @org.junit.Test
        public void allowedTest() {
            System.out.println(ALLOWED_OUTPUT);
        }

        @org.junit.Test
        public void rejectedTest() {
            System.out.println(REJECTED_OUTPUT);
        }
    }

    private static List<String> junitCoreCommand() {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(JUnitCoreMain.class.getName());
        command.add("--filter=" + NamePrefixFilterFactory.class.getName() + "=allowed");
        command.add(FilterTarget.class.getName());
        return command;
    }

    private static String javaExecutable() {
        return System.getProperty("java.home")
                + File.separator
                + "bin"
                + File.separator
                + "java";
    }

    public static final class JUnitCoreMain {
        private JUnitCoreMain() {
        }

        public static void main(String[] args) {
            JUnitCore.main(args);
        }
    }
}
