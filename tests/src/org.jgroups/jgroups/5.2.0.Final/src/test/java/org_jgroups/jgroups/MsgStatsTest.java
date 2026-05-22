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
    void rendersMessageStatisticsFromDeclaredFields() {
        MsgStats stats = new MsgStats()
                .incrNumMsgsSent(2)
                .incrNumMsgsReceived(3)
                .incrNumUcastMsgsSent(4)
                .incrNumMcastMsgsReceived(5)
                .incrNumBytesSent(6)
                .incrNumBytesReceived(7)
                .incrNumRejectedMsgs(8);

        String rendered = stats.toString();

        assertThat(rendered)
                .contains("num_msgs_sent: 2")
                .contains("num_msgs_received: 3")
                .contains("num_ucasts_sent: 4")
                .contains("num_mcasts_received: 5")
                .contains("num_bytes_sent: 6")
                .contains("num_bytes_received: 7")
                .contains("num_rejected_msgs: 8");
    }
}
