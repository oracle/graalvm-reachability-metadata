/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_mina.mina_core;

import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.demux.DemuxingProtocolCodecFactory;
import org.junit.jupiter.api.Test;

public class DemuxingProtocolCodecFactoryInnerDefaultConstructorMessageEncoderFactoryTest {
    @Test
    void constructsTheRegisteredEncoderToWriteAnIntegerMessage() throws Exception {
        DemuxingProtocolCodecFactory sender = new DemuxingProtocolCodecFactory();
        sender.register(MinaTestSupport.IntegerEncoder.class);
        DemuxingProtocolCodecFactory receiver = new DemuxingProtocolCodecFactory();
        receiver.register(new MinaTestSupport.IntegerDecoder());

        MinaTestSupport.assertTransfer(new ProtocolCodecFilter(receiver), new ProtocolCodecFilter(sender),
                65536, 65536);
    }
}
