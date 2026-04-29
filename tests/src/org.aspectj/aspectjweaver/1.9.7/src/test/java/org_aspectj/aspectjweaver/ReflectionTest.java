/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.util.Arrays;

import org.aspectj.util.Reflection;
import org.junit.jupiter.api.Test;

public class ReflectionTest {
    @Test
    void invokesMatchingMethodAndReadsStaticField() {
        InvocationFixture fixture = new InvocationFixture();

        Object result = Reflection.invokeN(InvocationFixture.class, "combine", fixture,
                new Object[] { "left", "right" });

        assertThat(result).isEqualTo("left:right");
        assertThat(Reflection.getStaticField(StaticFieldFixture.class, "PUBLIC_VALUE")).isEqualTo("aspectj-reflection");
    }

    @Test
    void runsMainResolvedFromCurrentVmByClassName() throws Exception {
        MainFixture.reset();

        Reflection.runMainInSameVM("", MainFixture.class.getName(), new String[] { "current", "vm" });

        assertThat(MainFixture.lastInvocation).isEqualTo("current,vm");
        assertThat(MainFixture.lastArguments).containsExactly("current", "vm");
    }

    @Test
    void runsMainResolvedThroughUtilityClassLoader() throws Exception {
        MainFixture.reset();

        Reflection.runMainInSameVM(new URL[0], new File[0], new File[0], MainFixture.class.getName(),
                new String[] { "utility", "loader" });

        assertThat(MainFixture.lastInvocation).isEqualTo("utility,loader");
        assertThat(MainFixture.lastArguments).containsExactly("utility", "loader");
    }

    public static class InvocationFixture {
        public String combine(String first, String second) {
            return first + ":" + second;
        }
    }

    public static class StaticFieldFixture {
        public static String PUBLIC_VALUE = "aspectj-reflection";
    }

    public static class MainFixture {
        public static String lastInvocation = "unset";
        public static String[] lastArguments = new String[0];

        public static void reset() {
            lastInvocation = "unset";
            lastArguments = new String[0];
        }

        public static void main(String[] args) {
            lastArguments = Arrays.copyOf(args, args.length);
            lastInvocation = String.join(",", args);
        }
    }
}
