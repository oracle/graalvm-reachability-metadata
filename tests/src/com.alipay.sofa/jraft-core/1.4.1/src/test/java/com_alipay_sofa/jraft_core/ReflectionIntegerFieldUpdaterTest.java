/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alipay_sofa.jraft_core;

import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.util.internal.IntegerFieldUpdater;
import com.alipay.sofa.jraft.util.internal.Updaters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReflectionIntegerFieldUpdaterTest {
    @Test
    void updatesPeerIndexThroughFieldUpdater() {
        PeerId peer = new PeerId("127.0.0.1", 8081, 2);
        IntegerFieldUpdater<PeerId> updater = Updaters.newIntegerFieldUpdater(PeerId.class, "idx");
        assertEquals(2, updater.get(peer));
        updater.set(peer, 5);
        assertEquals(5, updater.get(peer));
        assertEquals(5, peer.getIdx());
        assertEquals("127.0.0.1:8081:5", peer.toString());
    }
}
