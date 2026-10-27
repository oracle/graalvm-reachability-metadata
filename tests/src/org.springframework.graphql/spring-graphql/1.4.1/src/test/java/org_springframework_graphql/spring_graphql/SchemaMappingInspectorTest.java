/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_graphql.spring_graphql;

import java.util.Map;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.Test;

import org.springframework.core.ResolvableType;
import org.springframework.graphql.execution.SchemaMappingInspector;
import org.springframework.graphql.execution.SchemaReport;
import org.springframework.graphql.execution.SelfDescribingDataFetcher;

import static org.assertj.core.api.Assertions.assertThat;

public class SchemaMappingInspectorTest {

    @Test
    void inspectsPublicFieldsAndRecordLikeMethodsOnMappedTypes() {
        SelfDescribingDataFetcher<SchemaMappingSource> sourceFetcher = new SourceDataFetcher();
        GraphQLObjectType sourceType = GraphQLObjectType.newObject()
                .name("SchemaMappingSource")
                .field(field("publicValue"))
                .field(field("recordLikeValue"))
                .build();
        GraphQLObjectType queryType = GraphQLObjectType.newObject()
                .name("Query")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("person")
                        .type(sourceType)
                        .dataFetcher(sourceFetcher)
                        .build())
                .build();
        GraphQLSchema schema = GraphQLSchema.newSchema().query(queryType).build();

        Map<String, DataFetcher> queryFetchers = Map.of("person", sourceFetcher);
        Map<String, Map<String, DataFetcher>> fetchers = Map.of("Query", queryFetchers);
        SchemaReport report = SchemaMappingInspector.inspect(schema, fetchers);

        ExecutionResult result = GraphQL.newGraphQL(schema).build()
                .execute("{ person { publicValue recordLikeValue } }");

        Map<String, Object> data = result.getData();
        assertThat(result.getErrors()).isEmpty();
        assertThat(data).isEqualTo(Map.of(
                "person", Map.of("publicValue", "field", "recordLikeValue", "method")));
        assertThat(report.unmappedFields()).isEmpty();
        assertThat(report.unmappedRegistrations()).isEmpty();
    }

    private GraphQLFieldDefinition field(String name) {
        return GraphQLFieldDefinition.newFieldDefinition()
                .name(name)
                .type(Scalars.GraphQLString)
                .build();
    }

    private static final class SourceDataFetcher implements SelfDescribingDataFetcher<SchemaMappingSource> {

        @Override
        public SchemaMappingSource get(DataFetchingEnvironment environment) {
            return new SchemaMappingSource();
        }

        @Override
        public String getDescription() {
            return "schema mapping source";
        }

        @Override
        public ResolvableType getReturnType() {
            return ResolvableType.forClass(SchemaMappingSource.class);
        }

    }

}
