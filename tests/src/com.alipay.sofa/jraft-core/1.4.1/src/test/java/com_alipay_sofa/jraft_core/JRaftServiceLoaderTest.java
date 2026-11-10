/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alipay_sofa.jraft_core;

import com.alipay.sofa.jraft.JRaftServiceFactory;
import com.alipay.sofa.jraft.entity.EnumOutter.EntryType;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.codec.LogEntryCodecFactory;
import com.alipay.sofa.jraft.rpc.RaftRpcFactory;
import com.alipay.sofa.jraft.rpc.RpcRequests;
import com.alipay.sofa.jraft.util.JRaftServiceLoader;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class JRaftServiceLoaderTest {
    @Test
    void discoversCachesAndReloadsServiceFactoryForLogCodec() {
        JRaftServiceLoader<JRaftServiceFactory> loader = JRaftServiceLoader.load(JRaftServiceFactory.class);
        JRaftServiceFactory factory = loader.first();
        assertSame(factory, loader.first());
        assertSame(factory, loader.iterator().next());
        LogEntryCodecFactory codec = factory.createLogEntryCodecFactory();
        LogEntry entry = new LogEntry(EntryType.ENTRY_TYPE_CONFIGURATION);
        entry.setId(new LogId(17, 4));
        entry.setPeers(List.of(new PeerId("127.0.0.1", 8081), new PeerId("127.0.0.1", 8082)));
        entry.setOldPeers(List.of(new PeerId("127.0.0.1", 8083)));
        entry.setLearners(List.of(new PeerId("127.0.0.1", 8084)));
        entry.setOldLearners(List.of(new PeerId("127.0.0.1", 8085)));
        entry.setData(ByteBuffer.wrap("configuration data".getBytes(StandardCharsets.UTF_8)));
        entry.setChecksum(entry.checksum());
        LogEntry decoded = codec.decoder().decode(codec.encoder().encode(entry));
        assertNotNull(decoded);
        assertEquals(entry.getId(), decoded.getId());
        assertEquals(entry.getType(), decoded.getType());
        assertEquals(entry.getPeers(), decoded.getPeers());
        assertEquals(entry.getOldPeers(), decoded.getOldPeers());
        assertEquals(entry.getLearners(), decoded.getLearners());
        assertEquals(entry.getOldLearners(), decoded.getOldLearners());
        assertEquals(entry.getData(), decoded.getData());
        assertEquals(entry.getChecksum(), decoded.getChecksum());
        loader.reload();
        assertNotSame(factory, loader.first());
    }

    @Test
    void discoversRpcFactoryAndBuildsErrorResponse() {
        RaftRpcFactory factory = JRaftServiceLoader.load(RaftRpcFactory.class).first();
        RpcRequests.ErrorResponse response = (RpcRequests.ErrorResponse) factory.getRpcResponseFactory()
                .newResponse(null, 22, "Unknown group: %s", "example");
        assertEquals(22, response.getErrorCode());
        assertEquals("Unknown group: example", response.getErrorMsg());
    }
}
