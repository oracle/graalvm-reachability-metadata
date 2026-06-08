/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.ha.BadFencingConfigurationException;
import org.apache.hadoop.ha.HAServiceTarget;
import org.apache.hadoop.ha.NodeFencer;
import org.apache.hadoop.ha.ShellCommandFencer;
import org.apache.hadoop.util.Shell;
import org.junit.jupiter.api.Test;

public class ShellCommandFencerTest {
    @Test
    void tryFenceExecutesSuccessfulCommandForTarget() {
        ShellCommandFencer fencer = newConfiguredFencer();

        boolean result = fencer.tryFence(new StaticHAServiceTarget(), successfulCommand());

        assertThat(result).isTrue();
    }

    @Test
    void tryFenceReturnsFalseForFailingCommand() {
        ShellCommandFencer fencer = newConfiguredFencer();

        boolean result = fencer.tryFence(new StaticHAServiceTarget(), failingCommand());

        assertThat(result).isFalse();
    }

    @Test
    void checkArgsRejectsEmptyCommand() {
        ShellCommandFencer fencer = newConfiguredFencer();

        assertThatThrownBy(() -> fencer.checkArgs(""))
                .isInstanceOf(BadFencingConfigurationException.class)
                .hasMessageContaining("No argument passed");
    }

    private static ShellCommandFencer newConfiguredFencer() {
        ShellCommandFencer fencer = new ShellCommandFencer();
        fencer.setConf(new Configuration(false));
        return fencer;
    }

    private static String successfulCommand() {
        if (Shell.WINDOWS) {
            return "exit /b 0";
        }
        return "exit 0";
    }

    private static String failingCommand() {
        if (Shell.WINDOWS) {
            return "exit /b 7";
        }
        return "exit 7";
    }

    private static class StaticHAServiceTarget extends HAServiceTarget {
        private static final InetSocketAddress ADDRESS = InetSocketAddress.createUnresolved("localhost", 8020);

        @Override
        public InetSocketAddress getAddress() {
            return ADDRESS;
        }

        @Override
        public InetSocketAddress getZKFCAddress() {
            return ADDRESS;
        }

        @Override
        public NodeFencer getFencer() {
            return null;
        }

        @Override
        public void checkFencingConfigured() {
        }
    }
}
