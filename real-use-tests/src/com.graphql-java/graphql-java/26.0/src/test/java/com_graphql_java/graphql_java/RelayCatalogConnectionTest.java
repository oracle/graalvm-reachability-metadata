/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.relay.Connection;
import graphql.relay.Edge;
import graphql.relay.SimpleListConnection;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises GraphQL Java's Relay cursor-based connection pagination subsystem for a catalog API. */
public class RelayCatalogConnectionTest {

    @Test
    public void paginatesCatalogWithAnEndCursor() {
        GraphQL graphQL = buildGraphQL();

        ExecutionResult firstPage = graphQL.execute("""
                query CatalogPage {
                  books(first: 2) {
                    edges {
                      node { id title }
                      cursor
                    }
                    pageInfo {
                      startCursor
                      endCursor
                      hasPreviousPage
                      hasNextPage
                    }
                  }
                }
                """);

        assertTrue(firstPage.getErrors().isEmpty(), () -> firstPage.getErrors().toString());
        Map<?, ?> firstConnection = (Map<?, ?>) ((Map<?, ?>) firstPage.getData()).get("books");
        List<?> firstEdges = (List<?>) firstConnection.get("edges");
        assertEquals(List.of(
                Map.of("id", "book-1", "title", "GraphQL in Practice"),
                Map.of("id", "book-2", "title", "Reliable APIs")),
                firstEdges.stream().map(edge -> ((Map<?, ?>) edge).get("node")).toList());
        assertTrue(firstEdges.stream().allMatch(edge -> !((String) ((Map<?, ?>) edge).get("cursor")).isEmpty()));
        Map<?, ?> firstPageInfo = (Map<?, ?>) firstConnection.get("pageInfo");
        assertEquals(Boolean.FALSE, firstPageInfo.get("hasPreviousPage"));
        assertEquals(Boolean.TRUE, firstPageInfo.get("hasNextPage"));

        String endCursor = (String) firstPageInfo.get("endCursor");
        ExecutionResult secondPage = graphQL.execute(ExecutionInput.newExecutionInput()
                .query("""
                        query CatalogPage($after: String!) {
                          books(first: 2, after: $after) {
                            edges {
                              node { id title }
                              cursor
                            }
                            pageInfo {
                              hasPreviousPage
                              hasNextPage
                            }
                          }
                        }
                        """)
                .variables(Map.of("after", endCursor))
                .build());

        assertTrue(secondPage.getErrors().isEmpty(), () -> secondPage.getErrors().toString());
        Map<?, ?> secondConnection = (Map<?, ?>) ((Map<?, ?>) secondPage.getData()).get("books");
        List<?> secondEdges = (List<?>) secondConnection.get("edges");
        assertEquals(List.of(Map.of("id", "book-3", "title", "Query Planning")),
                secondEdges.stream().map(edge -> ((Map<?, ?>) edge).get("node")).toList());
        assertEquals(Map.of("hasPreviousPage", true, "hasNextPage", false),
                secondConnection.get("pageInfo"));
    }

    private static GraphQL buildGraphQL() {
        String schema = """
                type Query {
                  books(first: Int, after: String): BookConnection!
                }
                type BookConnection {
                  edges: [BookEdge!]!
                  pageInfo: PageInfo!
                }
                type BookEdge {
                  node: Book!
                  cursor: String!
                }
                type PageInfo {
                  startCursor: String
                  endCursor: String
                  hasPreviousPage: Boolean!
                  hasNextPage: Boolean!
                }
                type Book {
                  id: ID!
                  title: String!
                }
                """;
        TypeDefinitionRegistry registry = new SchemaParser().parse(schema);
        List<Book> books = List.of(
                new Book("book-1", "GraphQL in Practice"),
                new Book("book-2", "Reliable APIs"),
                new Book("book-3", "Query Planning"));
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("books",
                        environment -> new SimpleListConnection<>(books).get(environment)))
                .type("BookConnection", type -> type
                        .dataFetcher("edges", environment -> ((Connection<?>) environment.getSource()).getEdges())
                        .dataFetcher("pageInfo", environment ->
                                ((Connection<?>) environment.getSource()).getPageInfo()))
                .type("BookEdge", type -> type
                        .dataFetcher("node", environment -> ((Edge<?>) environment.getSource()).getNode())
                        .dataFetcher("cursor", environment ->
                                ((Edge<?>) environment.getSource()).getCursor().getValue()))
                .type("Book", type -> type
                        .dataFetcher("id", environment -> ((Book) environment.getSource()).id())
                        .dataFetcher("title", environment -> ((Book) environment.getSource()).title()))
                .build();
        GraphQLSchema executableSchema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQL.newGraphQL(executableSchema).build();
    }

    private record Book(String id, String title) {
    }
}
