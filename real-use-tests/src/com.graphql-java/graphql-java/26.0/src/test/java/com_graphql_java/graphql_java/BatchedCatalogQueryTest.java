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
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises GraphQL Java's schema parsing and batched data-fetching subsystem for a catalog query. */
public class BatchedCatalogQueryTest {

    @Test
    public void loadsRepeatedAuthorsInOneBatchAndReturnsCatalogData() throws Exception {
        Catalog catalog = new Catalog();
        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        DataLoader<String, Author> authors = DataLoaderFactory.newDataLoader(catalog::loadAuthors);
        dataLoaderRegistry.register("authors", authors);

        GraphQL graphQL = buildGraphQL(catalog);
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query("""
                        query CatalogPage {
                          books {
                            id
                            title
                            author {
                              id
                              name
                            }
                          }
                        }
                        """)
                .dataLoaderRegistry(dataLoaderRegistry)
                .build();

        ExecutionResult result = graphQL.executeAsync(input).get(30, TimeUnit.SECONDS);

        assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());
        assertEquals(List.of(
                Map.of("id", "book-1", "title", "GraphQL in Practice",
                        "author", Map.of("id", "author-1", "name", "Ada Lovelace")),
                Map.of("id", "book-2", "title", "Reliable APIs",
                        "author", Map.of("id", "author-2", "name", "Grace Hopper")),
                Map.of("id", "book-3", "title", "Query Planning",
                        "author", Map.of("id", "author-1", "name", "Ada Lovelace"))),
                ((Map<?, ?>) result.getData()).get("books"));
        assertEquals(List.of("author-1", "author-2"), catalog.batchKeys);
        assertEquals(1, catalog.batchCount);
    }

    private static GraphQL buildGraphQL(Catalog catalog) {
        String schema = """
                type Query {
                  books: [Book!]!
                }
                type Book {
                  id: ID!
                  title: String!
                  author: Author!
                }
                type Author {
                  id: ID!
                  name: String!
                }
                """;
        TypeDefinitionRegistry registry = new SchemaParser().parse(schema);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("books", environment -> catalog.books))
                .type("Book", type -> type
                        .dataFetcher("id", environment -> ((Book) environment.getSource()).id())
                        .dataFetcher("title", environment -> ((Book) environment.getSource()).title())
                        .dataFetcher("author", BatchedCatalogQueryTest::loadAuthor))
                .type("Author", type -> type
                        .dataFetcher("id", environment -> ((Author) environment.getSource()).id())
                        .dataFetcher("name", environment -> ((Author) environment.getSource()).name()))
                .build();
        GraphQLSchema executableSchema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQL.newGraphQL(executableSchema).build();
    }

    private static CompletableFuture<Author> loadAuthor(DataFetchingEnvironment environment) {
        Book book = (Book) environment.getSource();
        return environment.<String, Author>getDataLoader("authors").load(book.authorId());
    }

    private record Book(String id, String title, String authorId) {
    }

    private record Author(String id, String name) {
    }

    private static final class Catalog {
        private final List<Book> books = List.of(
                new Book("book-1", "GraphQL in Practice", "author-1"),
                new Book("book-2", "Reliable APIs", "author-2"),
                new Book("book-3", "Query Planning", "author-1"));
        private final List<String> batchKeys = new ArrayList<>();
        private int batchCount;

        private CompletableFuture<List<Author>> loadAuthors(List<String> keys) {
            batchCount++;
            batchKeys.addAll(keys);
            Map<String, Author> authors = new LinkedHashMap<>();
            authors.put("author-1", new Author("author-1", "Ada Lovelace"));
            authors.put("author-2", new Author("author-2", "Grace Hopper"));
            return CompletableFuture.completedFuture(keys.stream().map(authors::get).toList());
        }
    }
}
