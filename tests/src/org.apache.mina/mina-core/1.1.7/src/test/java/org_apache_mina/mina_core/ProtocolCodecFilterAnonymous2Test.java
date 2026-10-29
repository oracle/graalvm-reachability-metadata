/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_mina.mina_core;

import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineDecoder;
import org.apache.mina.filter.codec.textline.TextLineEncoder;
import org.junit.jupiter.api.Test;

public class ProtocolCodecFilterAnonymous2Test {
    @Test
    void createsTextLineCodecsForEachConnectedSession() throws Exception {
        ProtocolCodecFilter receiver = new ProtocolCodecFilter(TextLineEncoder.class, TextLineDecoder.class);
        ProtocolCodecFilter sender = new ProtocolCodecFilter(TextLineEncoder.class, TextLineDecoder.class);

        MinaTestSupport.assertTransfer(receiver, sender, "first request", "first request");
        MinaTestSupport.assertTransfer(receiver, sender, "second request", "second request");
    }
}
