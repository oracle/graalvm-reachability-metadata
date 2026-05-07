/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_parsing_3;

import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.macros.LogHelper;

public final class PekkoParsingLogHelperHarness implements LogHelper {
    private final LoggingAdapter loggingAdapter;

    public PekkoParsingLogHelperHarness(boolean debugEnabled, boolean infoEnabled, boolean warningEnabled) {
        this.loggingAdapter = new TestLoggingAdapter(debugEnabled, infoEnabled, warningEnabled);
    }

    @Override
    public LoggingAdapter log() {
        return loggingAdapter;
    }

    public boolean isDebugEnabledThroughHelper() {
        return isDebugEnabled();
    }

    public boolean isInfoEnabledThroughHelper() {
        return isInfoEnabled();
    }

    public boolean isWarningEnabledThroughHelper() {
        return isWarningEnabled();
    }

    public String prefixStringThroughHelper() {
        return prefixString();
    }

    private static final class TestLoggingAdapter implements LoggingAdapter {
        private final boolean debugEnabled;
        private final boolean infoEnabled;
        private final boolean warningEnabled;

        private TestLoggingAdapter(boolean debugEnabled, boolean infoEnabled, boolean warningEnabled) {
            this.debugEnabled = debugEnabled;
            this.infoEnabled = infoEnabled;
            this.warningEnabled = warningEnabled;
        }

        @Override
        public boolean isErrorEnabled() {
            return false;
        }

        @Override
        public boolean isWarningEnabled() {
            return warningEnabled;
        }

        @Override
        public boolean isInfoEnabled() {
            return infoEnabled;
        }

        @Override
        public boolean isDebugEnabled() {
            return debugEnabled;
        }

        @Override
        public void notifyError(String message) {
            throw new UnsupportedOperationException("error logging is not used by this test");
        }

        @Override
        public void notifyError(Throwable cause, String message) {
            throw new UnsupportedOperationException("error logging is not used by this test");
        }

        @Override
        public void notifyWarning(String message) {
            throw new UnsupportedOperationException("warning logging is not used by this test");
        }

        @Override
        public void notifyInfo(String message) {
            throw new UnsupportedOperationException("info logging is not used by this test");
        }

        @Override
        public void notifyDebug(String message) {
            throw new UnsupportedOperationException("debug logging is not used by this test");
        }
    }
}
