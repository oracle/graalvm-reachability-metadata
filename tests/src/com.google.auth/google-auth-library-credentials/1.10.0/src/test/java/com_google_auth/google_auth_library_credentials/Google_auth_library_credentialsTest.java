/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_credentials;

import com.google.auth.Credentials;
import com.google.auth.RequestMetadataCallback;
import com.google.auth.Retryable;
import com.google.auth.ServiceAccountSigner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class Google_auth_library_credentialsTest {
    private static final URI TEST_URI = URI.create("https://example.com/token");
    private static final Map<String, List<String>> TEST_METADATA = Map.of("Authorization", List.of("Bearer token"));

    @Test
    void getRequestMetadataWithoutUriDelegatesToUriSpecificMethod() throws IOException {
        RecordingCredentials credentials = new RecordingCredentials(TEST_METADATA);

        Map<String, List<String>> metadata = credentials.getRequestMetadata();

        assertThat(metadata).isEqualTo(TEST_METADATA);
        assertThat(credentials.lastUri).isNull();
    }

    @Test
    void getRequestMetadataAsyncSchedulesWorkOnExecutorAndReturnsMetadata() {
        RecordingCredentials credentials = new RecordingCredentials(TEST_METADATA);
        RecordingExecutor executor = new RecordingExecutor();
        RecordingCallback callback = new RecordingCallback();

        credentials.getRequestMetadata(TEST_URI, executor, callback);

        assertThat(executor.executeCount).isEqualTo(1);
        assertThat(executor.lastTask).isNotNull();
        assertThat(callback.successCount).isZero();
        assertThat(callback.failureCount).isZero();

        executor.lastTask.run();

        assertThat(credentials.lastUri).isEqualTo(TEST_URI);
        assertThat(callback.successCount).isEqualTo(1);
        assertThat(callback.failureCount).isZero();
        assertThat(callback.metadata).isEqualTo(TEST_METADATA);
    }

    @Test
    void getRequestMetadataAsyncSendsRuntimeFailuresToCallbackAfterExecutorRuns() {
        IllegalStateException failure = new IllegalStateException("boom");
        RecordingCredentials credentials = new RecordingCredentials(failure);
        RecordingExecutor executor = new RecordingExecutor();
        RecordingCallback callback = new RecordingCallback();

        credentials.getRequestMetadata(TEST_URI, executor, callback);

        assertThat(executor.executeCount).isEqualTo(1);
        assertThat(executor.lastTask).isNotNull();
        assertThat(callback.successCount).isZero();
        assertThat(callback.failureCount).isZero();

        executor.lastTask.run();

        assertThat(credentials.lastUri).isEqualTo(TEST_URI);
        assertThat(callback.successCount).isZero();
        assertThat(callback.failureCount).isEqualTo(1);
        assertThat(callback.failure).isSameAs(failure);
    }

    @Test
    void blockingGetToCallbackSendsFailuresToCallback() {
        RetryableIOException failure = new RetryableIOException("temporary failure", true, 3);
        RecordingCredentials credentials = new RecordingCredentials(failure);
        RecordingCallback callback = new RecordingCallback();

        credentials.invokeBlockingGetToCallback(TEST_URI, callback);

        assertThat(credentials.lastUri).isEqualTo(TEST_URI);
        assertThat(callback.successCount).isZero();
        assertThat(callback.failureCount).isEqualTo(1);
        assertThat(callback.failure).isSameAs(failure);
        assertThat(((Retryable) callback.failure).isRetryable()).isTrue();
        assertThat(((Retryable) callback.failure).getRetryCount()).isEqualTo(3);
    }

    @Test
    void signingExceptionStoresMessageAndCause() {
        RuntimeException cause = new RuntimeException("boom");

        ServiceAccountSigner.SigningException exception = new ServiceAccountSigner.SigningException("message", cause);

        assertThat(exception).hasMessage("message");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void signingExceptionImplementsEqualsAndHashCodeContract() {
        RuntimeException cause = new RuntimeException("boom");
        RuntimeException otherCause = new RuntimeException("other");
        ServiceAccountSigner.SigningException exception = new ServiceAccountSigner.SigningException("message", cause);
        ServiceAccountSigner.SigningException same = new ServiceAccountSigner.SigningException("message", cause);
        ServiceAccountSigner.SigningException differentMessage = new ServiceAccountSigner.SigningException("other-message", cause);
        ServiceAccountSigner.SigningException differentCause = new ServiceAccountSigner.SigningException("message", otherCause);

        assertThat(exception.equals(exception)).isTrue();
        assertThat(exception.equals("message")).isFalse();
        assertThat(exception).isEqualTo(same);
        assertThat(exception.hashCode()).isEqualTo(same.hashCode());
        assertThat(exception).isNotEqualTo(differentMessage);
        assertThat(exception).isNotEqualTo(differentCause);
    }

    private static final class RecordingCredentials extends Credentials {
        private final Map<String, List<String>> metadata;
        private final Throwable failure;
        private URI lastUri;

        private RecordingCredentials(Map<String, List<String>> metadata) {
            this.metadata = metadata;
            this.failure = null;
        }

        private RecordingCredentials(Throwable failure) {
            this.metadata = null;
            this.failure = failure;
        }

        @Override
        public String getAuthenticationType() {
            return "OAuth2";
        }

        @Override
        public Map<String, List<String>> getRequestMetadata(URI uri) throws IOException {
            lastUri = uri;
            if (failure instanceof IOException ioException) {
                throw ioException;
            }
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            return metadata;
        }

        @Override
        public boolean hasRequestMetadata() {
            return true;
        }

        @Override
        public boolean hasRequestMetadataOnly() {
            return true;
        }

        @Override
        public void refresh() {
        }

        private void invokeBlockingGetToCallback(URI uri, RequestMetadataCallback callback) {
            blockingGetToCallback(uri, callback);
        }
    }

    private static final class RecordingExecutor implements Executor {
        private int executeCount;
        private Runnable lastTask;

        @Override
        public void execute(Runnable command) {
            executeCount++;
            lastTask = command;
        }
    }

    private static final class RecordingCallback implements RequestMetadataCallback {
        private int successCount;
        private int failureCount;
        private Map<String, List<String>> metadata;
        private Throwable failure;

        @Override
        public void onSuccess(Map<String, List<String>> metadata) {
            successCount++;
            this.metadata = metadata;
        }

        @Override
        public void onFailure(Throwable exception) {
            failureCount++;
            failure = exception;
        }
    }

    private static final class RetryableIOException extends IOException implements Retryable {
        private final boolean retryable;
        private final int retryCount;

        private RetryableIOException(String message, boolean retryable, int retryCount) {
            super(message);
            this.retryable = retryable;
            this.retryCount = retryCount;
        }

        @Override
        public boolean isRetryable() {
            return retryable;
        }

        @Override
        public int getRetryCount() {
            return retryCount;
        }
    }
}
