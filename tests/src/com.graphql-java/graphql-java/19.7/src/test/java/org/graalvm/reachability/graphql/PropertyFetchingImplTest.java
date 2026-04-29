/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.reachability.graphql;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.PropertyDataFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static graphql.Scalars.GraphQLString;
import static org.assertj.core.api.Assertions.assertThat;

public class PropertyFetchingImplTest {

  public final String visible = "public field";

  private final String hidden = "declared field";

  @BeforeEach
  void resetPropertyFetcherConfiguration() {
    PropertyDataFetcher.clearReflectionCache();
    PropertyDataFetcher.setUseSetAccessible(true);
    PropertyDataFetcher.setUseNegativeCache(true);
  }

  @Test
  void fetchesPublicGetterWithNoArguments() {
    Object fetchedValue = fetchProperty("displayName", this);

    assertThat(fetchedValue).isEqualTo("public getter");
  }

  @Test
  void fetchesGetterWithDataFetchingEnvironmentArgument() {
    Object fetchedValue = fetchProperty("viewer", this);

    assertThat(fetchedValue).isEqualTo("environment getter");
  }

  @Test
  void fetchesAndCachesPublicField() {
    Object firstFetchedValue = fetchProperty("visible", this);
    Object cachedFetchedValue = fetchProperty("visible", this);

    assertThat(firstFetchedValue).isEqualTo("public field");
    assertThat(cachedFetchedValue).isEqualTo("public field");
  }

  @Test
  void fetchesDeclaredFieldAfterGetterLookupFails() {
    Object fetchedValue = fetchProperty("hidden", this);

    assertThat(fetchedValue).isEqualTo("declared field");
  }

  @Test
  void fetchesPublicMethodFromInaccessibleClass() {
    InaccessibleGetterSource source = new InaccessibleGetterSource();

    Object fetchedValue = fetchProperty("name", source);

    assertThat(fetchedValue).isEqualTo("package private getter");
  }

  public String getDisplayName() {
    return "public getter";
  }

  public String getViewer(DataFetchingEnvironment environment) {
    return environment == null ? "missing environment" : "environment getter";
  }

  private Object fetchProperty(String propertyName, Object source) {
    DataFetchingEnvironment environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
            .source(source)
            .fieldType(GraphQLString)
            .build();
    return PropertyDataFetcher.fetching(propertyName).get(environment);
  }
}

class InaccessibleGetterSource {

  public String getName() {
    return "package private getter";
  }
}
