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
    void toStringIncludesMessageCounters() {
        MsgStats stats = new MsgStats()
                .incrNumMsgsSent(3)
                .incrNumMsgsReceived(5)
                .incrNumUcastMsgsSent(7)
                .incrNumMcastMsgsReceived(11)
                .incrNumBytesSent(13)
                .incrNumRejectedMsgs(2);

        String description = stats.toString();

        assertThat(description)
                .contains("num_msgs_sent: 3")
                .contains("num_msgs_received: 5")
                .contains("num_ucasts_sent: 7")
                .contains("num_mcasts_received: 11")
                .contains("num_bytes_sent: 13")
                .contains("num_rejected_msgs: 2");
    }
}
