/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import graphql.Scalars;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.PropertyDataFetcherHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GraphqlJavaPropertyFetchingImplTest {

  @BeforeEach
  void clearCacheBeforeTest() {
    PropertyDataFetcherHelper.clearReflectionCache();
  }

  @AfterEach
  void clearCacheAfterTest() {
    PropertyDataFetcherHelper.clearReflectionCache();
    PropertyDataFetcherHelper.setUseSetAccessible(true);
    PropertyDataFetcherHelper.setUseNegativeCache(true);
  }

  @Test
  void fetchesPublicGetterAcceptingDataFetchingEnvironment() {
    DataFetchingEnvironment environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
            .fieldType(Scalars.GraphQLString)
            .build();
    EnvironmentAwareProperty source = new EnvironmentAwareProperty();

    Object value = PropertyDataFetcherHelper.getPropertyValue("message", source, Scalars.GraphQLString, environment);

    assertThat(value).isEqualTo("message:true");
  }

  @Test
  void fetchesPrivateGetterUsingSetAccessible() {
    PrivateGetterSource source = new PrivateGetterSource();

    Object value = PropertyDataFetcherHelper.getPropertyValue("secret", source, Scalars.GraphQLString);

    assertThat(value).isEqualTo("private-getter");
  }

  @Test
  void fetchesPublicFieldAndCachedPublicField() {
    PublicFieldSource source = new PublicFieldSource();

    Object firstValue = PropertyDataFetcherHelper.getPropertyValue("publicName", source, Scalars.GraphQLString);
    source.publicName = "cached-public-field";
    Object cachedValue = PropertyDataFetcherHelper.getPropertyValue("publicName", source, Scalars.GraphQLString);

    assertThat(firstValue).isEqualTo("public-field");
    assertThat(cachedValue).isEqualTo("cached-public-field");
  }

  @Test
  void fetchesPrivateFieldUsingSetAccessible() {
    PrivateFieldSource source = new PrivateFieldSource();

    Object firstValue = PropertyDataFetcherHelper.getPropertyValue("hiddenName", source, Scalars.GraphQLString);
    source.hiddenName = "cached-private-field";
    Object cachedValue = PropertyDataFetcherHelper.getPropertyValue("hiddenName", source, Scalars.GraphQLString);

    assertThat(firstValue).isEqualTo("private-field");
    assertThat(cachedValue).isEqualTo("cached-private-field");
  }

  @Test
  void fetchesPublicGetterFromNonPublicClassAfterPublicSearchFallback() {
    NonPublicGetterSource source = new NonPublicGetterSource();

    Object value = PropertyDataFetcherHelper.getPropertyValue("fallback", source, Scalars.GraphQLString);

    assertThat(value).isEqualTo("fallback");
  }

  public static final class EnvironmentAwareProperty {

    public String getMessage(DataFetchingEnvironment environment) {
      return "message:" + (environment.getFieldType() == Scalars.GraphQLString);
    }
  }

  private static final class PrivateGetterSource {

    private String getSecret() {
      return "private-getter";
    }
  }

  public static final class PublicFieldSource {

    public String publicName = "public-field";
  }

  private static final class PrivateFieldSource {

    private String hiddenName = "private-field";
  }

  private static final class NonPublicGetterSource {

    public String getFallback() {
      return "fallback";
    }
  }
}
