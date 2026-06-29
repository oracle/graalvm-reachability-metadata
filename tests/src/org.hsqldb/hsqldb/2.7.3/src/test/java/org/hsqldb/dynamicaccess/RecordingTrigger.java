/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.hsqldb.dynamicaccess;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.hsqldb.trigger.Trigger;

public final class RecordingTrigger implements Trigger {
    private static final AtomicReference<Event> LAST_EVENT = new AtomicReference<>();

    public RecordingTrigger() {
    }

    public static void reset() {
        LAST_EVENT.set(null);
    }

    public static Event lastEvent() {
        return LAST_EVENT.get();
    }

    @Override
    public void fire(
            int type,
            String triggerName,
            String tableName,
            String[] columnNames,
            Object[] oldRow,
            Object[] newRow) {

        LAST_EVENT.set(new Event(
                type,
                triggerName,
                tableName,
                copy(columnNames),
                copy(oldRow),
                copy(newRow)));
    }

    private static String[] copy(String[] values) {
        return values == null ? null : Arrays.copyOf(values, values.length);
    }

    private static Object[] copy(Object[] values) {
        return values == null ? null : Arrays.copyOf(values, values.length);
    }

    public record Event(
            int type,
            String triggerName,
            String tableName,
            String[] columnNames,
            Object[] oldRow,
            Object[] newRow) {
    }
}
