/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import graphql.execution.ExecutionContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.PropertyDataFetcher;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static graphql.Scalars.GraphQLString;
import static org.assertj.core.api.Assertions.assertThat;

public class PropertyFetchingImplTest {

  @BeforeEach
  void resetPropertyFetcher() {
    PropertyDataFetcher.clearReflectionCache();
  }

  @Test
  void invokesFunctionFetcherWithSourceObject() throws Exception {
    ZeroArgumentGetterSource source = new ZeroArgumentGetterSource();

    String value = PropertyDataFetcher.<String, ZeroArgumentGetterSource>fetching(
        ZeroArgumentGetterSource::getName).get(environmentFor(source));

    assertThat(value).isEqualTo("zero argument getter value");
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

  private DataFetchingEnvironment environmentFor(Object source) {
    ExecutionContext executionContext = new ExecutionContext(null, null, null, null, null, null, null,
        Collections.emptyMap(), null, null, Collections.emptyMap(), null, null);
    return new DataFetchingEnvironmentImpl(source, Collections.emptyMap(), null, null, null,
        Collections.emptyList(), GraphQLString, null, null, Collections.emptyMap(), null, null, null,
        executionContext);
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
}
