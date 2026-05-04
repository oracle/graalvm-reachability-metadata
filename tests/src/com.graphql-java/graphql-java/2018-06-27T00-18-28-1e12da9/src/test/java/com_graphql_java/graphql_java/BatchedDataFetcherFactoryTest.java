/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionTypeInfo;
import graphql.execution.batched.Batched;
import graphql.execution.batched.BatchedDataFetcher;
import graphql.execution.batched.BatchedDataFetcherFactory;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BatchedDataFetcherFactoryTest {

  @Test
  void createsBatchedFetcherFromAnnotatedDataFetcherMethod() {
    AnnotatedListFetcher supplied = new AnnotatedListFetcher();
    DataFetchingEnvironment environment = new TestDataFetchingEnvironment(List.of("Ada", "Bob"));

    BatchedDataFetcher fetcher = new BatchedDataFetcherFactory().create(supplied);
    Object result = fetcher.get(environment);

    assertThat(result).isEqualTo(List.of("Hello Ada", "Hello Bob"));
  }

  private static class TestDataFetchingEnvironment implements DataFetchingEnvironment {

    private final Object source;

    TestDataFetchingEnvironment(Object source) {
      this.source = source;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getSource() {
      return (T) source;
    }

    @Override
    public Map<String, Object> getArguments() {
      return Collections.emptyMap();
    }

    @Override
    public boolean containsArgument(String name) {
      return false;
    }

    @Override
    public <T> T getArgument(String name) {
      return null;
    }

    @Override
    public <T> T getContext() {
      return null;
    }

    @Override
    public <T> T getRoot() {
      return null;
    }

    @Override
    public GraphQLFieldDefinition getFieldDefinition() {
      return null;
    }

    @Override
    public List<Field> getFields() {
      return Collections.emptyList();
    }

    @Override
    public Field getField() {
      return null;
    }

    @Override
    public GraphQLOutputType getFieldType() {
      return null;
    }

    @Override
    public ExecutionTypeInfo getFieldTypeInfo() {
      return null;
    }

    @Override
    public GraphQLType getParentType() {
      return null;
    }

    @Override
    public GraphQLSchema getGraphQLSchema() {
      return null;
    }

    @Override
    public Map<String, FragmentDefinition> getFragmentsByName() {
      return Collections.emptyMap();
    }

    @Override
    public ExecutionId getExecutionId() {
      return null;
    }

    @Override
    public DataFetchingFieldSelectionSet getSelectionSet() {
      return null;
    }

    @Override
    public ExecutionContext getExecutionContext() {
      return null;
    }
  }

  public static class AnnotatedListFetcher implements DataFetcher<Object> {

    @Override
    @Batched
    public Object get(DataFetchingEnvironment environment) {
      List<String> names = environment.getSource();
      return names.stream()
          .map(name -> "Hello " + name)
          .collect(Collectors.toList());
    }
  }
}
