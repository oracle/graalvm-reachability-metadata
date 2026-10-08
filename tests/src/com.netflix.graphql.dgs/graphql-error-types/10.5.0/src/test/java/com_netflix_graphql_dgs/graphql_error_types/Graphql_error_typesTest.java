/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_netflix_graphql_dgs.graphql_error_types;

import com.netflix.graphql.types.errors.ErrorDetail;
import com.netflix.graphql.types.errors.ErrorType;
import com.netflix.graphql.types.errors.TypedGraphQLError;
import graphql.ErrorClassification;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class Graphql_error_typesTest {
    @Test
    void buildsTypedErrorsWithClassificationAndDiagnosticExtensions() {
        SourceLocation location = new SourceLocation(7, 11, "orders.graphql");
        TypedGraphQLError error = TypedGraphQLError.newBuilder()
                .message("Order %s could not be loaded", "order-7")
                .errorType(ErrorType.UNAVAILABLE)
                .location(location)
                .path(List.of("order", "items", 0))
                .extensions(Map.of("traceId", "trace-7"))
                .origin("orders-service")
                .debugUri("https://errors.example.test/orders/order-7")
                .debugInfo(Map.of("retryAfterSeconds", 5))
                .build();

        assertThat(error.getMessage()).isEqualTo("Order order-7 could not be loaded");
        assertThat(error.getErrorType()).isEqualTo(ErrorType.UNAVAILABLE);
        assertThat(error.getLocations()).containsExactly(location);
        assertThat(error.getPath()).containsExactly("order", "items", 0);
        assertThat(error.getExtensions())
                .containsEntry("traceId", "trace-7")
                .containsEntry("errorType", "UNAVAILABLE")
                .containsEntry("origin", "orders-service")
                .containsEntry("debugUri", "https://errors.example.test/orders/order-7")
                .containsEntry("debugInfo", Map.of("retryAfterSeconds", 5));
        assertThat(error.toSpecification())
                .containsEntry("message", "Order order-7 could not be loaded")
                .containsEntry("locations", List.of(Map.of("line", 7, "column", 11)))
                .containsEntry("path", List.of("order", "items", 0))
                .containsEntry("extensions", error.getExtensions());
    }

    @Test
    void acceptsGraphQlPathsAndAccumulatesLocations() {
        SourceLocation firstLocation = new SourceLocation(3, 5);
        SourceLocation secondLocation = new SourceLocation(9, 2);
        TypedGraphQLError error = TypedGraphQLError.newBuilder()
                .message("Viewer field could not be resolved")
                .locations(List.of(firstLocation))
                .location(secondLocation)
                .path(ResultPath.fromList(List.of("viewer", 1, "name")))
                .build();

        assertThat(error.getLocations()).containsExactly(firstLocation, secondLocation);
        assertThat(error.getPath()).containsExactly("viewer", 1, "name");
    }

    @Test
    void representsDetailedErrorClassificationsInExtensions() {
        TypedGraphQLError error = TypedGraphQLError.newConflictBuilder()
                .message("The order was changed by another request")
                .build();

        assertThat(error.getErrorType()).isEqualTo(ErrorDetail.Common.CONFLICT);
        assertThat(error.getExtensions())
                .containsEntry("errorType", "FAILED_PRECONDITION")
                .containsEntry("errorDetail", "CONFLICT");
        assertThat(ErrorDetail.Common.CONFLICT.toSpecification(error))
                .isEqualTo("FAILED_PRECONDITION.CONFLICT");
    }

    @Test
    void supportsCustomGraphQlErrorClassifications() {
        ErrorClassification classification = ErrorClassification.errorClassification("RATE_LIMITED");
        TypedGraphQLError error = TypedGraphQLError.newBuilder()
                .errorType(classification)
                .build();

        assertThat(error.getMessage()).isEqualTo("RATE_LIMITED");
        assertThat(error.getErrorType()).isSameAs(classification);
        assertThat(error.getExtensions()).containsEntry("classification", "RATE_LIMITED");
    }

    @Test
    void convenienceBuildersSelectTheirDocumentedErrorTypes() {
        assertThat(TypedGraphQLError.newInternalErrorBuilder().build().getErrorType())
                .isEqualTo(ErrorType.INTERNAL);
        assertThat(TypedGraphQLError.newNotFoundBuilder().build().getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        assertThat(TypedGraphQLError.newPermissionDeniedBuilder().build().getErrorType())
                .isEqualTo(ErrorType.PERMISSION_DENIED);
        assertThat(TypedGraphQLError.newBadRequestBuilder().build().getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(TypedGraphQLError.newConflictBuilder().build().getErrorType())
                .isEqualTo(ErrorDetail.Common.CONFLICT);

        TypedGraphQLError defaultError = TypedGraphQLError.newBuilder().build();
        assertThat(defaultError.getMessage()).isEqualTo("UNKNOWN");
        assertThat(defaultError.getErrorType()).isEqualTo(ErrorType.UNKNOWN);
        assertThat(defaultError.getExtensions()).containsEntry("errorType", "UNKNOWN");
    }

    @Test
    void commonErrorDetailsExposeTheirCoarseErrorTypes() {
        assertThat(ErrorDetail.Common.DEADLINE_EXCEEDED.getErrorType()).isEqualTo(ErrorType.UNAVAILABLE);
        assertThat(ErrorDetail.Common.ENHANCE_YOUR_CALM.getErrorType()).isEqualTo(ErrorType.UNAVAILABLE);
        assertThat(ErrorDetail.Common.TOO_MANY_REQUESTS.getErrorType()).isEqualTo(ErrorType.UNAVAILABLE);
        assertThat(ErrorDetail.Common.FIELD_NOT_FOUND.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(ErrorDetail.Common.INVALID_ARGUMENT.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(ErrorDetail.Common.INVALID_CURSOR.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        assertThat(ErrorDetail.Common.MISSING_RESOURCE.getErrorType()).isEqualTo(ErrorType.FAILED_PRECONDITION);
        assertThat(ErrorDetail.Common.CONFLICT.getErrorType()).isEqualTo(ErrorType.FAILED_PRECONDITION);
        assertThat(ErrorDetail.Common.SERIALIZATION_ERROR.getErrorType()).isEqualTo(ErrorType.INTERNAL);
        assertThat(ErrorDetail.Common.SERVICE_ERROR.getErrorType()).isEqualTo(ErrorType.UNAVAILABLE);
        assertThat(ErrorDetail.Common.THROTTLED_CONCURRENCY.getErrorType()).isEqualTo(ErrorType.UNAVAILABLE);
        assertThat(ErrorDetail.Common.THROTTLED_CPU.getErrorType()).isEqualTo(ErrorType.UNAVAILABLE);
        assertThat(ErrorDetail.Common.UNIMPLEMENTED.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);

        TypedGraphQLError detailedError = TypedGraphQLError.newBuilder()
                .errorDetail(ErrorDetail.Common.INVALID_ARGUMENT)
                .build();
        assertThat(ErrorDetail.Common.INVALID_ARGUMENT.toSpecification(detailedError))
                .isEqualTo("BAD_REQUEST.INVALID_ARGUMENT");
    }

    @Test
    void directConstructionAndGraphQlEqualityPreserveErrorData() {
        List<SourceLocation> locations = List.of(new SourceLocation(2, 4));
        List<Object> path = List.of("viewer", "name");
        Map<String, Object> extensions = Map.of("errorType", "INTERNAL", "requestId", "req-2");

        TypedGraphQLError first = new TypedGraphQLError(
                "Could not load viewer",
                locations,
                ErrorType.INTERNAL,
                path,
                extensions);
        TypedGraphQLError equal = new TypedGraphQLError(
                "Could not load viewer",
                locations,
                ErrorType.INTERNAL,
                path,
                extensions);

        assertThat(first).isEqualTo(equal);
        assertThat(first).hasSameHashCodeAs(equal);
        assertThat(first.getLocations()).containsExactlyElementsOf(locations);
        assertThat(first.getPath()).containsExactlyElementsOf(path);
        assertThat(first.getExtensions()).containsExactlyInAnyOrderEntriesOf(extensions);
        assertThat(first.toSpecification()).containsEntry("extensions", extensions);
    }
}
