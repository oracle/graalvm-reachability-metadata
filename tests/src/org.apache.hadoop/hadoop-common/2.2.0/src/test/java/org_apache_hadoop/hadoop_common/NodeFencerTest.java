/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.ha.BadFencingConfigurationException;
import org.apache.hadoop.ha.HAServiceTarget;
import org.apache.hadoop.ha.NodeFencer;
import org.apache.hadoop.ha.ShellCommandFencer;
import org.apache.hadoop.util.Shell;
import org.junit.jupiter.api.Test;

public class NodeFencerTest {
    private static final String FENCING_KEY = "dfs.ha.fencing.methods";

    @Test
    void createLoadsFenceMethodFromFullyQualifiedClassName()
            throws BadFencingConfigurationException {
        Configuration conf = new Configuration(false);
        String methodSpec = ShellCommandFencer.class.getName() + "(" + successfulCommand() + ")";
        conf.set(FENCING_KEY, methodSpec);

        NodeFencer fencer = NodeFencer.create(conf, FENCING_KEY);

        assertThat(fencer).isNotNull();
        assertThat(fencer.fence(new StaticHAServiceTarget())).isTrue();
    }

    private static String successfulCommand() {
        if (Shell.WINDOWS) {
            return "exit /b 0";
        }
        return "exit 0";
    }

    private static class StaticHAServiceTarget extends HAServiceTarget {
        private static final InetSocketAddress ADDRESS =
                InetSocketAddress.createUnresolved("localhost", 8020);

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
