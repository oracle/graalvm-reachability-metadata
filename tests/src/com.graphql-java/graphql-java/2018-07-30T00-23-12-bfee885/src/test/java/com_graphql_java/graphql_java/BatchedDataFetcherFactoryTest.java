/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.batched.BatchedDataFetcher;
import graphql.execution.batched.BatchedDataFetcherFactory;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.StaticDataFetcher;
import org.junit.jupiter.api.Test;

import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;
import static org.assertj.core.api.Assertions.assertThat;

public class BatchedDataFetcherFactoryTest {

  @Test
  void wrapsOrdinaryDataFetcherForBatchedExecution() throws Exception {
    StaticDataFetcher suppliedFetcher = new StaticDataFetcher("constant value");
    DataFetchingEnvironment environment = newDataFetchingEnvironment()
        .source(List.of("Leia", "Luke"))
        .executionContext(newExecutionContext())
        .build();

    BatchedDataFetcher batchedFetcher = new BatchedDataFetcherFactory().create(suppliedFetcher);
    CompletableFuture<?> result = (CompletableFuture<?>) batchedFetcher.get(environment);

    assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(List.of("constant value", "constant value"));
  }

  private ExecutionContext newExecutionContext() {
    return new ExecutionContext(
        null,
        ExecutionId.from("batched-data-fetcher-factory-test"),
        null,
        null,
        null,
        null,
        null,
        Collections.emptyMap(),
        null,
        null,
        Collections.emptyMap(),
        null,
        null);
  }
}
