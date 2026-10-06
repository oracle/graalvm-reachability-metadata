/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static graphql.Scalars.GraphQLString;
import static org.assertj.core.api.Assertions.assertThat;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.PropertyDataFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PropertyFetchingImplTest {
    @BeforeEach
    void resetPropertyFetcher() {
        PropertyDataFetcher.clearReflectionCache();
        PropertyDataFetcher.setUseSetAccessible(true);
        PropertyDataFetcher.setUseNegativeCache(true);
    }

    @Test
    void invokesPublicGettersWithAndWithoutEnvironment() throws Exception {
        EnvironmentAwareSource environmentSource = new EnvironmentAwareSource();
        DataFetchingEnvironment environment = environmentFor(environmentSource);

        assertThat(PropertyDataFetcher.<String>fetching("value").get(environment))
                .isEqualTo("environment value");
        assertThat(environmentSource.environment).isSameAs(environment);

        assertThat(PropertyDataFetcher.<String>fetching("name").get(environmentFor(new GetterSource())))
                .isEqualTo("getter value");
    }

    @Test
    void invokesPrivateGetterViaAccessibleLookup() throws Exception {
        assertThat(PropertyDataFetcher.<String>fetching("secret")
                        .get(environmentFor(new PrivateGetterSource())))
                .isEqualTo("private getter value");
    }

    @Test
    void readsPublicAndPrivateFieldsFromCache() throws Exception {
        PropertyDataFetcher<String> publicFetcher = PropertyDataFetcher.fetching("publicField");
        PublicFieldSource publicSource = new PublicFieldSource();
        assertThat(publicFetcher.get(environmentFor(publicSource))).isEqualTo("public field value");
        assertThat(publicFetcher.get(environmentFor(publicSource))).isEqualTo("public field value");

        PropertyDataFetcher<String> privateFetcher = PropertyDataFetcher.fetching("privateField");
        PrivateFieldSource privateSource = new PrivateFieldSource();
        assertThat(privateFetcher.get(environmentFor(privateSource))).isEqualTo("private field value");
        assertThat(privateFetcher.get(environmentFor(privateSource))).isEqualTo("private field value");
    }

    private static DataFetchingEnvironment environmentFor(Object source) {
        return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .source(source)
                .fieldType(GraphQLString)
                .build();
    }

    public static class EnvironmentAwareSource {
        private DataFetchingEnvironment environment;

        public String getValue(DataFetchingEnvironment environment) {
            this.environment = environment;
            return "environment value";
        }
    }

    public static class GetterSource {
        public String getName() {
            return "getter value";
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
