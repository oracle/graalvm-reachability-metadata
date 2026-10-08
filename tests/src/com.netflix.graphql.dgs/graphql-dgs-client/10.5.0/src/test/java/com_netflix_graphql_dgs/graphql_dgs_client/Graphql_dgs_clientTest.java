/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_netflix_graphql_dgs.graphql_dgs_client;

import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.HttpResponse;
import com.netflix.graphql.dgs.client.RequestDetails;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class Graphql_dgs_clientTest {
    @Test
    void parsesDataErrorsHeadersAndNestedValues() {
        Map<String, List<String>> headers = Map.of("x-request-id", List.of("header-request"));
        String json = """
                {
                  "data": {
                    "viewer": {"id": "u-7", "name": "Ada"},
                    "numbers": [1, 2],
                    "gatewayRequestDetails": {
                      "requestId": "request-42",
                      "edgarLink": "https://example.test/request-42"
                    }
                  },
                  "errors": [{
                    "message": "partial result",
                    "path": ["viewer", "name"],
                    "locations": [{"line": 1, "column": 2}],
                    "extensions": {"code": "PARTIAL"}
                  }]
                }
                """;

        GraphQLResponse response = new GraphQLResponse(json, headers);

        assertThat(response.getJson()).isEqualTo(json);
        assertThat(response.getHeaders()).containsEntry("x-request-id", List.of("header-request"));
        assertThat(response.getData()).containsKey("viewer");
        assertThat((Object) response.getParsed().read("$.data.viewer.id")).isEqualTo("u-7");
        assertThat((Object) response.extractValue("viewer.id")).isEqualTo("u-7");
        assertThat(response.extractValueAsObject("viewer", Viewer.class))
                .isEqualTo(new Viewer("u-7", "Ada"));
        assertThat(response.dataAsObject(Data.class).viewer()).isEqualTo(new Viewer("u-7", "Ada"));
        assertThat(response.hasErrors()).isTrue();
        assertThat(response.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getMessage()).isEqualTo("partial result");
            assertThat(error.getPathAsString()).isEqualTo("viewer.name");
        });
        assertThat(response.getRequestDetails().getRequestId()).isEqualTo("request-42");
    }

    @Test
    void customClientSendsQueriesVariablesAndOperationNameToExecutor() {
        AtomicReference<String> requestUrl = new AtomicReference<>();
        AtomicReference<Map<String, ? extends List<String>>> requestHeaders = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        GraphQLClient client = GraphQLClient.createCustom("https://graphql.example.test", (url, headers, body) -> {
            requestUrl.set(url);
            requestHeaders.set(headers);
            requestBody.set(body);
            return new HttpResponse(200, "{\"data\":{\"greeting\":\"hello\"}}");
        });

        GraphQLResponse response = client.executeQuery(
                "query Greeting($name: String!) { greeting }",
                Map.of("name", "Ada"),
                "Greeting");

        assertThat(response.hasErrors()).isFalse();
        assertThat((Object) response.extractValue("greeting")).isEqualTo("hello");
        assertThat(requestUrl).hasValue("https://graphql.example.test");
        assertThat(requestHeaders).hasValueSatisfying(headers -> assertThat(headers).containsKey("Content-Type"));
        assertThat(requestBody).hasValueSatisfying(body -> assertThat(body)
                .contains("Greeting")
                .contains("name")
                .contains("Ada")
                .contains("query Greeting"));
    }

    public record Data(Viewer viewer, List<Integer> numbers, RequestDetails gatewayRequestDetails) {
    }

    public record Viewer(String id, String name) {
    }
}
