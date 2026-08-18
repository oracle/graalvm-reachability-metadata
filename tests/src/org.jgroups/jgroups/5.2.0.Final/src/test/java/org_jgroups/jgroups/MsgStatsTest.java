/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.MsgStats;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MsgStatsTest {
    @Test
    void describesCurrentMessageCounters() {
        MsgStats stats = new MsgStats()
                .incrNumMsgsSent(3)
                .incrNumUcastMsgsSent(2)
                .incrNumMcastMsgsSent(1)
                .incrNumMsgsReceived(4)
                .incrNumOOBMsgsReceived(1)
                .incrNumBytesSent(16)
                .incrNumBytesReceived(32)
                .incrNumRejectedMsgs(2);

        String description = stats.toString();

        assertThat(description)
                .contains("num_msgs_sent: 3")
                .contains("num_ucasts_sent: 2")
                .contains("num_mcasts_sent: 1")
                .contains("num_msgs_received: 4")
                .contains("num_oob_msgs_received: 1")
                .contains("num_bytes_sent: 16")
                .contains("num_bytes_received: 32")
                .contains("num_rejected_msgs: 2");
        assertThat(stats.getNumMsgsSent()).isEqualTo(3);
        assertThat(stats.getNumMsgsReceived()).isEqualTo(4);
        assertThat(stats.getNumRejectedMsgs()).isEqualTo(2);
    }
}
