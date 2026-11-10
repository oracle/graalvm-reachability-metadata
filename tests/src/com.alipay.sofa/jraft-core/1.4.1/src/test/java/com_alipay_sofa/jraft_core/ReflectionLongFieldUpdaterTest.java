/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alipay_sofa.jraft_core;

import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.util.internal.LongFieldUpdater;
import com.alipay.sofa.jraft.util.internal.Updaters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReflectionLongFieldUpdaterTest {
    @Test
    void updatesLogIndexWithoutNarrowingLongValues() {
        LogId id = new LogId(21, 3);
        LongFieldUpdater<LogId> updater = Updaters.newLongFieldUpdater(LogId.class, "index");
        assertEquals(21, updater.get(id));
        long newIndex = (1L << 40) + 7;
        updater.set(id, newIndex);
        assertEquals(newIndex, updater.get(id));
        assertEquals(new LogId(newIndex, 3), id);
    }
}
