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

import static org.junit.jupiter.api.Assertions.assertThrows;

public class DemuxingProtocolCodecFactoryTest {
    @Test
    void registersMessageCodecClassesForAnIntegerProtocol() throws Exception {
        DemuxingProtocolCodecFactory factory = new DemuxingProtocolCodecFactory();
        factory.register(MinaTestSupport.IntegerEncoder.class);
        factory.register(MinaTestSupport.IntegerDecoder.class);

        MinaTestSupport.assertTransfer(new ProtocolCodecFilter(factory), new ProtocolCodecFilter(factory),
                1024, 1024);
    }

    @Test
    void rejectsClassesThatAreNotMessageCodecs() {
        DemuxingProtocolCodecFactory factory = new DemuxingProtocolCodecFactory();

        assertThrows(IllegalArgumentException.class, () -> factory.register(String.class));
    }
}
