/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_asynchttpclient.async_http_client;

import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.filter.ReleasePermitOnComplete;
import org.junit.jupiter.api.Test;

import io.netty.handler.codec.http.HttpHeaders;

import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;

public class ReleasePermitOnCompleteTest {
    @Test
    void releasesPermitAfterSuccessfulCompletion() throws Exception {
        Semaphore available = new Semaphore(0);
        RecordingAsyncHandler handler = new RecordingAsyncHandler();

        AsyncHandler<String> wrapped = ReleasePermitOnComplete.wrap(handler, available);

        assertThat(wrapped.onStatusReceived(null)).isEqualTo(AsyncHandler.State.CONTINUE);
        assertThat(handler.statusCallbacks).isEqualTo(1);
        assertThat(available.availablePermits()).isZero();

        assertThat(wrapped.onCompleted()).isEqualTo("completed");

        assertThat(handler.completions).isEqualTo(1);
        assertThat(available.tryAcquire()).isTrue();
        assertThat(available.availablePermits()).isZero();
    }

    @Test
    void releasesPermitAfterThrowableNotification() {
        Semaphore available = new Semaphore(0);
        RecordingAsyncHandler handler = new RecordingAsyncHandler();
        RuntimeException failure = new RuntimeException("request failed");

        AsyncHandler<String> wrapped = ReleasePermitOnComplete.wrap(handler, available);
        wrapped.onThrowable(failure);

        assertThat(handler.throwables).isEqualTo(1);
        assertThat(handler.lastThrowable).isSameAs(failure);
        assertThat(available.tryAcquire()).isTrue();
        assertThat(available.availablePermits()).isZero();
    }

    private static final class RecordingAsyncHandler implements AsyncHandler<String> {
        private int statusCallbacks;
        private int completions;
        private int throwables;
        private Throwable lastThrowable;

        @Override
        public State onStatusReceived(HttpResponseStatus responseStatus) {
            statusCallbacks++;
            return State.CONTINUE;
        }

        @Override
        public State onHeadersReceived(HttpHeaders headers) {
            return State.CONTINUE;
        }

        @Override
        public State onBodyPartReceived(HttpResponseBodyPart bodyPart) {
            return State.CONTINUE;
        }

        @Override
        public void onThrowable(Throwable t) {
            throwables++;
            lastThrowable = t;
        }

        @Override
        public String onCompleted() {
            completions++;
            return "completed";
        }
    }
}
