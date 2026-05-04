/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opencensus.opencensus_contrib_grpc_util;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.grpc.Metadata;
import io.grpc.Status.Code;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.opencensus.contrib.grpc.util.StatusConverter;
import io.opencensus.trace.Status.CanonicalCode;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Opencensus_contrib_grpc_utilTest {
    private static final Map<Code, CanonicalCode> STATUS_MAPPINGS = Map.ofEntries(
            entry(Code.OK, CanonicalCode.OK),
            entry(Code.CANCELLED, CanonicalCode.CANCELLED),
            entry(Code.UNKNOWN, CanonicalCode.UNKNOWN),
            entry(Code.INVALID_ARGUMENT, CanonicalCode.INVALID_ARGUMENT),
            entry(Code.DEADLINE_EXCEEDED, CanonicalCode.DEADLINE_EXCEEDED),
            entry(Code.NOT_FOUND, CanonicalCode.NOT_FOUND),
            entry(Code.ALREADY_EXISTS, CanonicalCode.ALREADY_EXISTS),
            entry(Code.PERMISSION_DENIED, CanonicalCode.PERMISSION_DENIED),
            entry(Code.RESOURCE_EXHAUSTED, CanonicalCode.RESOURCE_EXHAUSTED),
            entry(Code.FAILED_PRECONDITION, CanonicalCode.FAILED_PRECONDITION),
            entry(Code.ABORTED, CanonicalCode.ABORTED),
            entry(Code.OUT_OF_RANGE, CanonicalCode.OUT_OF_RANGE),
            entry(Code.UNIMPLEMENTED, CanonicalCode.UNIMPLEMENTED),
            entry(Code.INTERNAL, CanonicalCode.INTERNAL),
            entry(Code.UNAVAILABLE, CanonicalCode.UNAVAILABLE),
            entry(Code.DATA_LOSS, CanonicalCode.DATA_LOSS),
            entry(Code.UNAUTHENTICATED, CanonicalCode.UNAUTHENTICATED));

    @Test
    void convertsEveryGrpcCodeToMatchingOpenCensusCanonicalCode() {
        assertThat(STATUS_MAPPINGS.keySet()).containsExactlyInAnyOrder(Code.values());

        for (Map.Entry<Code, CanonicalCode> mapping : STATUS_MAPPINGS.entrySet()) {
            assertThat(StatusConverter.fromGrpcCode(mapping.getKey()))
                    .as("OpenCensus canonical code for gRPC code %s", mapping.getKey())
                    .isEqualTo(mapping.getValue());
        }
    }

    @Test
    void convertsEveryOpenCensusCanonicalCodeToMatchingGrpcCode() {
        assertThat(STATUS_MAPPINGS.values()).containsExactlyInAnyOrder(CanonicalCode.values());

        for (Map.Entry<Code, CanonicalCode> mapping : STATUS_MAPPINGS.entrySet()) {
            assertThat(StatusConverter.toGrpcCode(mapping.getValue()))
                    .as("gRPC code for OpenCensus canonical code %s", mapping.getValue())
                    .isEqualTo(mapping.getKey());
        }
    }

    @Test
    void convertsGrpcStatusesWithoutDescriptions() {
        for (Map.Entry<Code, CanonicalCode> mapping : STATUS_MAPPINGS.entrySet()) {
            io.grpc.Status grpcStatus = io.grpc.Status.fromCode(mapping.getKey());

            io.opencensus.trace.Status convertedStatus = StatusConverter.fromGrpcStatus(grpcStatus);

            assertThat(convertedStatus.getCanonicalCode())
                    .as("canonical code converted from gRPC status %s", grpcStatus)
                    .isEqualTo(mapping.getValue());
            assertThat(convertedStatus.getDescription()).isNull();
            assertThat(convertedStatus.isOk()).isEqualTo(mapping.getKey() == Code.OK);
        }
    }

    @Test
    void convertsOpenCensusStatusesWithoutDescriptions() {
        for (Map.Entry<Code, CanonicalCode> mapping : STATUS_MAPPINGS.entrySet()) {
            io.opencensus.trace.Status opencensusStatus = mapping.getValue().toStatus();

            io.grpc.Status convertedStatus = StatusConverter.toGrpcStatus(opencensusStatus);

            assertThat(convertedStatus.getCode())
                    .as("gRPC code converted from OpenCensus status %s", opencensusStatus)
                    .isEqualTo(mapping.getKey());
            assertThat(convertedStatus.getDescription()).isNull();
            assertThat(convertedStatus.isOk()).isEqualTo(mapping.getValue() == CanonicalCode.OK);
        }
    }

    @Test
    void copiesGrpcStatusDescriptionsToOpenCensusStatuses() {
        for (Map.Entry<Code, CanonicalCode> mapping : STATUS_MAPPINGS.entrySet()) {
            String description = "gRPC description for " + mapping.getKey().name();
            io.grpc.Status grpcStatus = io.grpc.Status.fromCode(mapping.getKey())
                    .withDescription(description)
                    .withCause(new IllegalStateException("cause is not part of OpenCensus status"));

            io.opencensus.trace.Status convertedStatus = StatusConverter.fromGrpcStatus(grpcStatus);

            assertThat(convertedStatus.getCanonicalCode()).isEqualTo(mapping.getValue());
            assertThat(convertedStatus.getDescription()).isEqualTo(description);
        }
    }

    @Test
    void copiesOpenCensusStatusDescriptionsToGrpcStatuses() {
        for (Map.Entry<Code, CanonicalCode> mapping : STATUS_MAPPINGS.entrySet()) {
            String description = "OpenCensus description for " + mapping.getValue().name();
            io.opencensus.trace.Status opencensusStatus = mapping.getValue().toStatus().withDescription(description);

            io.grpc.Status convertedStatus = StatusConverter.toGrpcStatus(opencensusStatus);

            assertThat(convertedStatus.getCode()).isEqualTo(mapping.getKey());
            assertThat(convertedStatus.getDescription()).isEqualTo(description);
            assertThat(convertedStatus.getCause()).isNull();
        }
    }

    @Test
    void preservesEmptyDescriptionsInBothDirections() {
        io.opencensus.trace.Status fromGrpc = StatusConverter.fromGrpcStatus(
                io.grpc.Status.UNAVAILABLE.withDescription(""));
        io.grpc.Status fromOpenCensus = StatusConverter.toGrpcStatus(
                io.opencensus.trace.Status.UNAVAILABLE.withDescription(""));

        assertThat(fromGrpc.getCanonicalCode()).isEqualTo(CanonicalCode.UNAVAILABLE);
        assertThat(fromGrpc.getDescription()).isEmpty();
        assertThat(fromOpenCensus.getCode()).isEqualTo(Code.UNAVAILABLE);
        assertThat(fromOpenCensus.getDescription()).isEmpty();
    }

    @Test
    void roundTripsGrpcStatusCodeAndDescriptionThroughOpenCensusStatus() {
        for (Map.Entry<Code, CanonicalCode> mapping : STATUS_MAPPINGS.entrySet()) {
            String description = "round trip from gRPC " + mapping.getKey().name();
            io.grpc.Status grpcStatus = io.grpc.Status.fromCode(mapping.getKey()).withDescription(description);

            io.grpc.Status roundTrippedStatus = StatusConverter.toGrpcStatus(
                    StatusConverter.fromGrpcStatus(grpcStatus));

            assertThat(roundTrippedStatus.getCode()).isEqualTo(mapping.getKey());
            assertThat(roundTrippedStatus.getDescription()).isEqualTo(description);
            assertThat(roundTrippedStatus.getCause()).isNull();
        }
    }

    @Test
    void roundTripsOpenCensusStatusCodeAndDescriptionThroughGrpcStatus() {
        for (Map.Entry<Code, CanonicalCode> mapping : STATUS_MAPPINGS.entrySet()) {
            String description = "round trip from OpenCensus " + mapping.getValue().name();
            io.opencensus.trace.Status opencensusStatus = mapping.getValue().toStatus().withDescription(description);

            io.opencensus.trace.Status roundTrippedStatus = StatusConverter.fromGrpcStatus(
                    StatusConverter.toGrpcStatus(opencensusStatus));

            assertThat(roundTrippedStatus.getCanonicalCode()).isEqualTo(mapping.getValue());
            assertThat(roundTrippedStatus.getDescription()).isEqualTo(description);
        }
    }

    @Test
    void convertsGrpcStatusExtractedFromRuntimeException() {
        String description = "authenticated user lacks the required permission";
        StatusRuntimeException exception = io.grpc.Status.PERMISSION_DENIED
                .withDescription(description)
                .asRuntimeException();

        io.opencensus.trace.Status convertedStatus = StatusConverter.fromGrpcStatus(
                io.grpc.Status.fromThrowable(exception));

        assertThat(convertedStatus.getCanonicalCode()).isEqualTo(CanonicalCode.PERMISSION_DENIED);
        assertThat(convertedStatus.getDescription()).isEqualTo(description);
    }

    @Test
    void convertedOpenCensusStatusCanBeUsedAsGrpcCheckedExceptionStatus() {
        String description = "quota was exhausted while handling the RPC";
        io.opencensus.trace.Status opencensusStatus = io.opencensus.trace.Status.RESOURCE_EXHAUSTED
                .withDescription(description);

        StatusException exception = StatusConverter.toGrpcStatus(opencensusStatus).asException();
        io.grpc.Status recoveredStatus = io.grpc.Status.fromThrowable(exception);

        assertThat(recoveredStatus.getCode()).isEqualTo(Code.RESOURCE_EXHAUSTED);
        assertThat(recoveredStatus.getDescription()).isEqualTo(description);
        assertThat(StatusConverter.fromGrpcStatus(recoveredStatus)).isEqualTo(opencensusStatus);
    }

    @Test
    void convertedOpenCensusStatusCanBeUsedAsGrpcRuntimeExceptionStatusWithTrailers() {
        Metadata.Key<String> retryHintKey = Metadata.Key.of("retry-hint", Metadata.ASCII_STRING_MARSHALLER);
        Metadata trailers = new Metadata();
        trailers.put(retryHintKey, "do-not-retry");
        String description = "response frame could not be decoded";
        io.opencensus.trace.Status opencensusStatus = io.opencensus.trace.Status.DATA_LOSS
                .withDescription(description);

        StatusRuntimeException exception = StatusConverter.toGrpcStatus(opencensusStatus)
                .asRuntimeException(trailers);
        io.grpc.Status recoveredStatus = io.grpc.Status.fromThrowable(exception);
        Metadata recoveredTrailers = io.grpc.Status.trailersFromThrowable(exception);

        assertThat(recoveredStatus.getCode()).isEqualTo(Code.DATA_LOSS);
        assertThat(recoveredStatus.getDescription()).isEqualTo(description);
        assertThat(StatusConverter.fromGrpcStatus(recoveredStatus)).isEqualTo(opencensusStatus);
        assertThat(recoveredTrailers).isNotNull();
        assertThat(recoveredTrailers.get(retryHintKey)).isEqualTo("do-not-retry");
    }

    @Test
    void rejectsNullInputs() {
        assertThatNullPointerException().isThrownBy(() -> StatusConverter.fromGrpcCode(null));
        assertThatNullPointerException().isThrownBy(() -> StatusConverter.fromGrpcStatus(null));
        assertThatNullPointerException().isThrownBy(() -> StatusConverter.toGrpcCode(null));
        assertThatNullPointerException().isThrownBy(() -> StatusConverter.toGrpcStatus(null));
    }
}
