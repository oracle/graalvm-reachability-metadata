/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.juli.logging.Log;

public final class RecordingLog implements Log {
    private final String name;
    private Object lastMessage;
    private Throwable lastThrowable;

    public RecordingLog() {
        this(null);
    }

    public RecordingLog(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Object getLastMessage() {
        return lastMessage;
    }

    public Throwable getLastThrowable() {
        return lastThrowable;
    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public boolean isFatalEnabled() {
        return true;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void trace(Object message) {
        record(message, null);
    }

    @Override
    public void trace(Object message, Throwable throwable) {
        record(message, throwable);
    }

    @Override
    public void debug(Object message) {
        record(message, null);
    }

    @Override
    public void debug(Object message, Throwable throwable) {
        record(message, throwable);
    }

    @Override
    public void info(Object message) {
        record(message, null);
    }

    @Override
    public void info(Object message, Throwable throwable) {
        record(message, throwable);
    }

    @Override
    public void warn(Object message) {
        record(message, null);
    }

    @Override
    public void warn(Object message, Throwable throwable) {
        record(message, throwable);
    }

    @Override
    public void error(Object message) {
        record(message, null);
    }

    @Override
    public void error(Object message, Throwable throwable) {
        record(message, throwable);
    }

    @Override
    public void fatal(Object message) {
        record(message, null);
    }

    @Override
    public void fatal(Object message, Throwable throwable) {
        record(message, throwable);
    }

    private void record(Object message, Throwable throwable) {
        lastMessage = message;
        lastThrowable = throwable;
    }
}
