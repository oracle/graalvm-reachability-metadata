/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.antlr_runtime;

import java.io.IOException;

import org.antlr.runtime.MismatchedTokenException;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.debug.BlankDebugEventListener;
import org.antlr.runtime.debug.DebugEventListener;
import org.antlr.runtime.debug.RemoteDebugEventSocketListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RemoteDebugEventSocketListenerTest {
    @Test
    void dispatchDeserializesRecognitionExceptionFromRemoteEvent() throws Exception {
        CapturingDebugEventListener listener = new CapturingDebugEventListener();
        DispatchingRemoteDebugEventSocketListener socketListener =
                new DispatchingRemoteDebugEventSocketListener(listener);

        socketListener.dispatchEvent(exceptionEvent(MismatchedTokenException.class, 7, 11, 13));

        RecognitionException exception = listener.recognitionException;
        assertThat(exception).isInstanceOf(MismatchedTokenException.class);
        assertThat(exception.index).isEqualTo(7);
        assertThat(exception.line).isEqualTo(11);
        assertThat(exception.charPositionInLine).isEqualTo(13);
    }

    private static String exceptionEvent(
            Class<? extends RecognitionException> exceptionClass, int index, int line, int charPositionInLine) {
        return String.join(
                "\t",
                "exception",
                exceptionClass.getName(),
                Integer.toString(index),
                Integer.toString(line),
                Integer.toString(charPositionInLine));
    }

    private static final class DispatchingRemoteDebugEventSocketListener extends RemoteDebugEventSocketListener {
        private DispatchingRemoteDebugEventSocketListener(DebugEventListener listener) throws IOException {
            super(listener, "localhost", 0);
        }

        @Override
        protected boolean openConnection() {
            return true;
        }

        private void dispatchEvent(String eventLine) {
            dispatch(eventLine);
        }
    }

    private static final class CapturingDebugEventListener extends BlankDebugEventListener {
        private RecognitionException recognitionException;

        @Override
        public void recognitionException(RecognitionException e) {
            recognitionException = e;
        }
    }
}
