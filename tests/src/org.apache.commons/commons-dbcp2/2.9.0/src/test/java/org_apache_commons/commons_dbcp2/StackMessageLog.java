/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.logging.impl.SimpleLog;

import java.io.Serial;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class StackMessageLog extends SimpleLog {
    @Serial
    private static final long serialVersionUID = 1L;
    //Checkstyle: stop field name check
    private static final Stack<String> messageStack = new Stack<>();
    //Checkstyle: resume field name check
    private static final Lock lock = new ReentrantLock();

    public static void clear() {
        lock.lock();
        try {
            messageStack.clear();
        } finally {
            lock.unlock();
        }
    }

    public static List<String> getAll() {
        final Iterator<String> iterator = messageStack.iterator();
        final List<String> messages = new ArrayList<>();
        while (iterator.hasNext()) {
            messages.add(iterator.next());
        }
        return messages;
    }

    public static boolean isEmpty() {
        return messageStack.isEmpty();
    }

    public static void lock() {
        lock.lock();
    }

    public static String popMessage() {
        String ret = null;
        lock.lock();
        try {
            ret = messageStack.pop();
        } catch (final EmptyStackException ignored) {
        } finally {
            lock.unlock();
        }
        return ret;
    }

    public static void unLock() {
        try {
            lock.unlock();
        } catch (final IllegalMonitorStateException ignored) {
        }
    }

    public StackMessageLog(final String name) {
        super(name);
    }

    @Override
    protected void log(final int type, final Object message, final Throwable t) {
        lock.lock();
        try {
            final StringBuilder buf = new StringBuilder();
            buf.append(message.toString());
            if (t != null) {
                buf.append(" <");
                buf.append(t);
                buf.append(">");
                final java.io.StringWriter sw = new java.io.StringWriter(1024);
                final java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                t.printStackTrace(pw);
                pw.close();
                buf.append(sw);
            }
            messageStack.push(buf.toString());
        } finally {
            lock.unlock();
        }
    }
}
