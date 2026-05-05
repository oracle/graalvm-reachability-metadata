/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionTypeInfo;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.PropertyDataFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static graphql.Scalars.GraphQLString;
import static org.assertj.core.api.Assertions.assertThat;

public class PropertyDataFetcherTest {

  @BeforeEach
  void resetPropertyFetcher() {
    PropertyDataFetcher.clearReflectionCache();
  }

  @Test
  void invokesFunctionFetcherWithSource() throws Exception {
    FunctionSource source = new FunctionSource();

    String value = PropertyDataFetcher.<String, FunctionSource>fetching(FunctionSource::value)
        .get(environmentFor(source));

    assertThat(value).isEqualTo("value from " + FunctionSource.class.getSimpleName());
  }

  @Test
  void invokesPublicZeroArgumentGetter() throws Exception {
    ZeroArgumentGetterSource source = new ZeroArgumentGetterSource();

    String value = PropertyDataFetcher.<String>fetching("name").get(environmentFor(source));

    assertThat(value).isEqualTo("zero argument getter value");
  }

  @Test
  void invokesPrivateGetterViaSetAccessibleLookup() throws Exception {
    PrivateGetterSource source = new PrivateGetterSource();

    String value = PropertyDataFetcher.<String>fetching("secret").get(environmentFor(source));

    assertThat(value).isEqualTo("private getter value");
  }

  @Test
  void readsAndCachesPublicField() throws Exception {
    PublicFieldSource source = new PublicFieldSource();
    PropertyDataFetcher<String> fetcher = PropertyDataFetcher.fetching("publicField");

    assertThat(fetcher.get(environmentFor(source))).isEqualTo("public field value");
    assertThat(fetcher.get(environmentFor(source))).isEqualTo("public field value");
  }

  @Test
  void readsAndCachesPrivateFieldViaSetAccessibleLookup() throws Exception {
    PrivateFieldSource source = new PrivateFieldSource();
    PropertyDataFetcher<String> fetcher = PropertyDataFetcher.fetching("privateField");

    assertThat(fetcher.get(environmentFor(source))).isEqualTo("private field value");
    assertThat(fetcher.get(environmentFor(source))).isEqualTo("private field value");
  }

  @Test
  void findsPublicMethodOnRootInterfaceWhenNoSuperclassExists() throws Throwable {
    PropertyDataFetcher<String> fetcher = PropertyDataFetcher.fetching("name");
    // A non-public interface has no superclass, so it exercises the root interface lookup path.
    MethodHandle finder = MethodHandles.privateLookupIn(PropertyDataFetcher.class, MethodHandles.lookup())
        .findVirtual(
            PropertyDataFetcher.class,
            "findPubliclyAccessibleMethod",
            MethodType.methodType(Method.class, Class.class, String.class));

    Method method = (Method) finder.invoke(fetcher, NonPublicNameSource.class, "getName");

    assertThat(method.getDeclaringClass()).isEqualTo(NonPublicNameSource.class);
    assertThat(method.getName()).isEqualTo("getName");
  }

  private DataFetchingEnvironment environmentFor(Object source) {
    return new TestDataFetchingEnvironment(source, GraphQLString);
  }

  private static class TestDataFetchingEnvironment implements DataFetchingEnvironment {

    private final Object source;
    private final GraphQLOutputType fieldType;

    TestDataFetchingEnvironment(Object source, GraphQLOutputType fieldType) {
      this.source = source;
      this.fieldType = fieldType;
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
      return fieldType;
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

  public static class FunctionSource {

    public String value() {
      return "value from " + getClass().getSimpleName();
    }
  }

  public static class ZeroArgumentGetterSource {

    public String getName() {
      return "zero argument getter value";
    }
  }

  public static class PrivateGetterSource {

    private String getSecret() {
      return "private getter value";
    }
  }

  public static class PublicFieldSource {

    public final String publicField = "public field value";
  }

  public static class PrivateFieldSource {

    private final String privateField = "private field value";
  }

  interface NonPublicNameSource {

    String getName();
  }
}
