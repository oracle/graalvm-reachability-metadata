/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_barchart_udt.barchart_udt_bundle;

import static org.assertj.core.api.Assertions.assertThat;

import com.barchart.udt.MonitorUDT;
import com.barchart.udt.SocketUDT;
import com.barchart.udt.TypeUDT;
import org.junit.jupiter.api.Test;

public class MonitorUDTTest {
    @Test
    void appendSnapshotIncludesSocketAndNumericMonitorFields() throws Exception {
        final SocketUDT socket = new SocketUDT(TypeUDT.STREAM);
        try {
            final MonitorUDT monitor = socket.monitor();
            final StringBuilder snapshot = new StringBuilder();

            monitor.appendSnapshot(snapshot);

            assertThat(snapshot.toString())
                    .contains(String.format("[id: 0x%08x]", socket.id()))
                    .contains("msTimeStamp = ")
                    .contains("pktSentTotal = ")
                    .contains("pktRecvTotal = ")
                    .contains("mbpsSendRate = ")
                    .contains("mbpsRecvRate = ")
                    .contains("byteAvailSndBuf = ")
                    .contains("byteAvailRcvBuf = ")
                    .contains("% localSendLoss = ")
                    .contains("% localReceiveLoss = ");
        } finally {
            socket.close();
        }
    }

    @Test
    void toStringUsesSnapshotFormatting() throws Exception {
        final SocketUDT socket = new SocketUDT(TypeUDT.DATAGRAM);
        try {
            final String snapshot = socket.monitor().toString();

            assertThat(snapshot)
                    .contains(String.format("[id: 0x%08x]", socket.id()))
                    .contains("pktSent = ")
                    .contains("pktRecv = ")
                    .contains("pktFlowWindow = ")
                    .contains("pktCongestionWindow = ")
                    .contains("msRTT = ")
                    .contains("mbpsBandwidth = ");
        } finally {
            socket.close();
        }
    }
}
