/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_mina.mina_core;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.demux.DemuxingProtocolCodecFactory;
import org.junit.jupiter.api.Test;

public class DemuxingProtocolCodecFactoryInnerDefaultConstructorMessageDecoderFactoryTest {
    @Test
    void constructsTheRegisteredDecoderToReadAnIntegerMessage() throws Exception {
        DemuxingProtocolCodecFactory factory = new DemuxingProtocolCodecFactory();
        factory.register(MinaTestSupport.IntegerDecoder.class);
        ByteBuffer message = ByteBuffer.allocate(Integer.BYTES);
        message.putInt(-37);
        message.flip();

        MinaTestSupport.assertTransfer(new ProtocolCodecFilter(factory), null, message, -37);
    }
}
