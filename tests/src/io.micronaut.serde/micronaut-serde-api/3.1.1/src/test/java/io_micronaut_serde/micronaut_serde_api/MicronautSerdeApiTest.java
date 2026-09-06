/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_serde.micronaut_serde_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import org.junit.jupiter.api.Test;

public class MicronautSerdeApiTest {

    @Test
    void roundTripsNestedSerdeableModels() throws Exception {
        LibraryBook expected = sampleBook();

        try (ApplicationContext context = ApplicationContext.run()) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            String json = mapper.writeValueAsString(expected);

            assertThat(json)
                    .contains("\"isbn\":\"978-0-00-000001-1\"")
                    .contains("\"title\":\"The \\\"Native\\\" Guide\"")
                    .contains("\"format\":\"HARDCOVER\"")
                    .contains("\"keywords\":[\"java\",\"serialization\"]");
            assertThat(mapper.readValue(json, LibraryBook.class)).isEqualTo(expected);
        }
    }

    @Test
    void roundTripsGenericCollectionsUsingArguments() throws Exception {
        LibraryBook first = sampleBook();
        LibraryBook second = new LibraryBook(
                "978-0-00-000002-8",
                "Data Formats",
                new Author("Grace Hopper", 1906),
                BookFormat.EBOOK,
                List.of("data", "json"),
                Map.of("central", 7));
        List<LibraryBook> expectedList = List.of(first, second);
        Map<String, LibraryBook> expectedMap = new LinkedHashMap<>();
        expectedMap.put("featured", first);
        expectedMap.put("new-release", second);

        try (ApplicationContext context = ApplicationContext.run()) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            Argument<List<LibraryBook>> listType = Argument.listOf(LibraryBook.class);
            Argument<Map<String, LibraryBook>> mapType = Argument.mapOf(String.class, LibraryBook.class);

            String listJson = mapper.writeValueAsString(listType, expectedList);
            List<LibraryBook> decodedList = mapper.readValue(listJson, listType);
            assertThat(decodedList).containsExactlyElementsOf(expectedList);

            String mapJson = mapper.writeValueAsString(mapType, expectedMap);
            Map<String, LibraryBook> decodedMap = mapper.readValue(mapJson, mapType);
            assertThat(decodedMap).containsExactlyEntriesOf(expectedMap);
        }
    }

    @Test
    void supportsTypedByteStreamAndTreeOperations() throws Exception {
        LibraryBook expected = sampleBook();
        Argument<LibraryBook> bookType = Argument.of(LibraryBook.class);

        try (ApplicationContext context = ApplicationContext.run()) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            byte[] encoded = mapper.writeValueAsBytes(bookType, expected);
            assertThat(mapper.readValue(encoded, bookType)).isEqualTo(expected);

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                mapper.writeValue(output, bookType, expected);
                try (ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray())) {
                    assertThat(mapper.readValue(input, bookType)).isEqualTo(expected);
                }
            }

            JsonNode tree = mapper.writeValueToTree(bookType, expected);
            assertThat(tree.get("author").get("name").getStringValue()).isEqualTo("Ada Lovelace");
            assertThat(mapper.readValueFromTree(tree, bookType)).isEqualTo(expected);
        }
    }

    private static LibraryBook sampleBook() {
        return new LibraryBook(
                "978-0-00-000001-1",
                "The \"Native\" Guide",
                new Author("Ada Lovelace", 1815),
                BookFormat.HARDCOVER,
                List.of("java", "serialization"),
                Map.of("central", 3, "west", 2));
    }

    @Serdeable
    public record Author(String name, int birthYear) {
    }

    public enum BookFormat {
        EBOOK,
        HARDCOVER
    }

    @Serdeable
    public record LibraryBook(
            String isbn,
            String title,
            Author author,
            BookFormat format,
            List<String> keywords,
            Map<String, Integer> copiesByBranch) {
    }
}
