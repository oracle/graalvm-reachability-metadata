/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.File;
import java.net.URL;
import java.util.Arrays;

import org.aspectj.util.Reflection;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionTest {
    @Test
    void invokesPublicMethodSelectedByNameAndArgumentCount() {
        SampleOperations target = new SampleOperations("aspectj");

        Object result = Reflection.invoke(
                SampleOperations.class,
                target,
                "format",
                "weaver",
                Integer.valueOf(7)
        );

        assertThat(result).isEqualTo("aspectj:weaver:7");
    }

    @Test
    void readsPublicStaticField() {
        Object value = Reflection.getStaticField(StaticFieldHolder.class, "MESSAGE");

        assertThat(value).isEqualTo("metadata-ready");
    }

    @Test
    void runsMainByNameOnCurrentClassPath() throws Exception {
        MainRecorder.reset();

        Reflection.runMainInSameVM("", MainRecorder.class.getName(), new String[] {"by-name", "current-classpath"});

        assertThat(MainRecorder.invocations()).isEqualTo(1);
        assertThat(MainRecorder.arguments()).containsExactly("by-name", "current-classpath");
    }

    @Test
    void runsMainThroughUtilityClassLoader() throws Exception {
        MainRecorder.reset();

        boolean completed = false;
        try {
            Reflection.runMainInSameVM(
                    new URL[0],
                    new File[0],
                    new File[0],
                    MainRecorder.class.getName(),
                    new String[] {"utility-loader"}
            );
            completed = true;
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }

        if (completed) {
            assertThat(MainRecorder.invocations()).isEqualTo(1);
            assertThat(MainRecorder.arguments()).containsExactly("utility-loader");
        }
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public static final class SampleOperations {
        private final String prefix;

        private SampleOperations(String prefix) {
            this.prefix = prefix;
        }

        public String format(String value, Integer count) {
            return prefix + ":" + value + ":" + count;
        }
    }

    public static final class StaticFieldHolder {
        public static final String MESSAGE = "metadata-ready";

        private StaticFieldHolder() {
        }
    }

    public static final class MainRecorder {
        private static int invocationCount;
        private static String[] arguments = new String[0];

        private MainRecorder() {
        }

        public static void main(String[] args) {
            invocationCount++;
            arguments = Arrays.copyOf(args, args.length);
        }

        static void reset() {
            invocationCount = 0;
            arguments = new String[0];
        }

        static int invocations() {
            return invocationCount;
        }

        static String[] arguments() {
            return Arrays.copyOf(arguments, arguments.length);
        }
    }
}
