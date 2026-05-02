/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import java.lang.reflect.Method;
import java.util.Arrays;

import graphql.schema.PropertyFetchingImpl;
import org.junit.jupiter.api.Test;

import static graphql.Scalars.GraphQLString;
import static org.assertj.core.api.Assertions.assertThat;

public class PropertyFetchingImplTest {

  @Test
  void resolvesPublicGetterWithSingleArgumentValue() {
    PropertyFetchingImpl fetcher = new PropertyFetchingImpl(String.class);
    SingleArgumentPojo pojo = new SingleArgumentPojo("graphql");

    Object value = fetcher.getPropertyValue("decoratedName", pojo, GraphQLString, "java");

    assertThat(value).isEqualTo("graphql-java");
  }

  @Test
  void resolvesPrivateGetterViaSetAccessibleLookup() {
    PropertyFetchingImpl fetcher = new PropertyFetchingImpl(Object.class);
    PrivateGetterPojo pojo = new PrivateGetterPojo("hidden getter");

    Object value = fetcher.getPropertyValue("secret", pojo, GraphQLString, null);

    assertThat(value).isEqualTo("hidden getter");
  }

  @Test
  void resolvesPublicFieldAndThenCachedPublicField() {
    PropertyFetchingImpl fetcher = new PropertyFetchingImpl(Object.class);
    PublicFieldPojo pojo = new PublicFieldPojo("public field");

    Object firstValue = fetcher.getPropertyValue("label", pojo, GraphQLString, null);
    Object cachedValue = fetcher.getPropertyValue("label", pojo, GraphQLString, null);

    assertThat(firstValue).isEqualTo("public field");
    assertThat(cachedValue).isEqualTo("public field");
  }

  @Test
  void resolvesPrivateFieldViaDeclaredFieldLookupAndThenCachedField() {
    PropertyFetchingImpl fetcher = new PropertyFetchingImpl(Object.class);
    PrivateFieldPojo pojo = new PrivateFieldPojo("private field");

    Object firstValue = fetcher.getPropertyValue("label", pojo, GraphQLString, null);
    Object cachedValue = fetcher.getPropertyValue("label", pojo, GraphQLString, null);

    assertThat(firstValue).isEqualTo("private field");
    assertThat(cachedValue).isEqualTo("private field");
  }

  @Test
  void resolvesPublicMethodFallbackForNonPublicInterface() throws Exception {
    PropertyFetchingImpl fetcher = new PropertyFetchingImpl(Object.class);
    Method lookupMethod = Arrays.stream(PropertyFetchingImpl.class.getDeclaredMethods())
        .filter(method -> method.getName().equals("findPubliclyAccessibleMethod"))
        .findFirst()
        .orElseThrow();
    lookupMethod.setAccessible(true);

    Method getter = (Method) lookupMethod.invoke(fetcher, null, FallbackGetter.class, "getFallbackName", false);

    assertThat(getter.getName()).isEqualTo("getFallbackName");
    assertThat(getter.getDeclaringClass()).isEqualTo(FallbackGetter.class);
  }

  public static final class SingleArgumentPojo {
    private final String name;

    private SingleArgumentPojo(String name) {
      this.name = name;
    }

    public String getDecoratedName(String suffix) {
      return name + "-" + suffix;
    }
  }

  private static final class PrivateGetterPojo {
    private final String secret;

    private PrivateGetterPojo(String secret) {
      this.secret = secret;
    }

    private String getSecret() {
      return secret;
    }
  }

  public static final class PublicFieldPojo {
    public final String label;

    private PublicFieldPojo(String label) {
      this.label = label;
    }
  }

  private static final class PrivateFieldPojo {
    private final String label;

    private PrivateFieldPojo(String label) {
      this.label = label;
    }
  }

  private interface FallbackGetter {
    String getFallbackName();
  }

}
