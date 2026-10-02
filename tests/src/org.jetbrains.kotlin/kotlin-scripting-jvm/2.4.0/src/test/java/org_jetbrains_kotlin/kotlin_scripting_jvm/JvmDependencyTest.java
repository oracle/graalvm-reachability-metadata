/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm;

import java.io.File;
import java.util.List;
import kotlin.script.experimental.jvm.JvmDependency;
import org.junit.jupiter.api.Test;

public class JvmDependencyTest {
    public static void main(String[] args) {
        verifyConfiguredClasspath();
    }

    @Test
    public void preservesConfiguredClasspath() {
        verifyConfiguredClasspath();
    }

    private static void verifyConfiguredClasspath() {
        List<File> classpath = List.of(new File("first.jar"), new File("second.jar"));
        JvmDependency dependency = new JvmDependency(classpath);

        if (!dependency.getClasspath().equals(classpath)) {
            throw new AssertionError("JvmDependency did not preserve its configured classpath");
        }
    }
}
