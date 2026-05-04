/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.PropertyDataFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static graphql.Scalars.GraphQLString;
import static org.assertj.core.api.Assertions.assertThat;

public class PropertyFetchingImplTest {

  @BeforeEach
  void resetPropertyFetcher() {
    PropertyDataFetcher.clearReflectionCache();
    PropertyDataFetcher.setUseSetAccessible(true);
    PropertyDataFetcher.setUseNegativeCache(true);
  }

  @Test
  void invokesPublicGetterWithDataFetchingEnvironmentArgument() throws Exception {
    EnvironmentAwareSource source = new EnvironmentAwareSource();
    DataFetchingEnvironment environment = environmentFor(source);

    String value = PropertyDataFetcher.<String>fetching("value").get(environment);

    assertThat(value).isEqualTo("value from " + EnvironmentAwareSource.class.getSimpleName());
    assertThat(source.seenEnvironment).isSameAs(environment);
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
    return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
        .source(source)
        .fieldType(GraphQLString)
        .build();
  }

  public static class EnvironmentAwareSource {

    private DataFetchingEnvironment seenEnvironment;

    public String getValue(DataFetchingEnvironment environment) {
      this.seenEnvironment = environment;
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
}
