/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

public final class FaultSupport {
    private FaultSupport() {
    }

    public static class SnapshotException extends RuntimeException {
        private final int term;

        public SnapshotException(String message, int term) {
            super(message);
            this.term = term;
            setStackTrace(new StackTraceElement[0]);
        }

        public int getTerm() {
            return term;
        }
    }
}
