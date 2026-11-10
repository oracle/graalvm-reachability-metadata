/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alipay_sofa.jraft_core;

import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.util.internal.ReferenceFieldUpdater;
import com.alipay.sofa.jraft.util.internal.Updaters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class ReflectionReferenceFieldUpdaterTest {
    @Test
    void replacesLogEntryIdentifierThroughFieldUpdater() {
        LogEntry entry = new LogEntry();
        ReferenceFieldUpdater<LogEntry, LogId> updater = Updaters.newReferenceFieldUpdater(LogEntry.class, "id");
        assertSame(entry.getId(), updater.get(entry));
        LogId replacement = new LogId(42, 8);
        updater.set(entry, replacement);
        assertSame(replacement, updater.get(entry));
        assertSame(replacement, entry.getId());
        LogId next = new LogId(43, 8);
        entry.setId(next);
        assertSame(next, updater.get(entry));
    }
}
