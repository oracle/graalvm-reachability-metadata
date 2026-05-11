/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_sbt.junit_interface;

import com.novocode.junit.JUnitFramework;
import org.junit.jupiter.api.Test;
import sbt.testing.Runner;

import static org.assertj.core.api.Assertions.assertThat;

public class JUnitRunnerTest {
    @Test
    void createsConfiguredRunListenerThroughFrameworkRunnerApi() {
        String listenerClassName = "org.junit.runner.notification.RunListener";
        String[] args = {"--run-listener=" + listenerClassName};
        String[] remoteArgs = {"remote-argument"};
        ClassLoader testClassLoader = JUnitRunnerTest.class.getClassLoader();

        Runner runner = new JUnitFramework().runner(args, remoteArgs, testClassLoader);

        assertThat(runner.args()).containsExactly(args);
        assertThat(runner.remoteArgs()).containsExactly(remoteArgs);
        assertThat(runner.done()).isEmpty();
    }
}
