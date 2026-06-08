/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_fastjson2.fastjson2;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONB;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONValidator;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.TypeReference;
import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.filter.ValueFilter;
import com.alibaba.fastjson2.schema.JSONSchema;
import com.alibaba.fastjson2.schema.ValidateResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Fastjson2Test {
    @Test
    void parsesAndWritesAnnotatedBeanGraph() {
        String json = """
                {
                  "id": 101,
                  "book_title": "Native Java",
                  "price": 39.99,
                  "published_at": "2024-06-01",
                  "available": true,
                  "tags": ["json", "graalvm"],
                  "author": {"name": "Ada", "active": true}
                }
                """;

        Book book = JSON.parseObject(json, Book.class, JSONReader.Feature.SupportSmartMatch);

        assertThat(book.getId()).isEqualTo(101L);
        assertThat(book.getTitle()).isEqualTo("Native Java");
        assertThat(book.getPrice()).isEqualByComparingTo(new BigDecimal("39.99"));
        assertThat(book.getPublishedAt()).isEqualTo(LocalDate.of(2024, 6, 1));
        assertThat(book.isAvailable()).isTrue();
        assertThat(book.getTags()).containsExactly("json", "graalvm");
        assertThat(book.getAuthor().getName()).isEqualTo("Ada");

        String serialized = JSON.toJSONString(
                book,
                JSONWriter.Feature.WriteNulls,
                JSONWriter.Feature.SortMapEntriesByKeys);
        JSONObject object = JSON.parseObject(serialized);
        assertThat(object.getLongValue("id")).isEqualTo(101L);
        assertThat(object.getString("title")).isEqualTo("Native Java");
        assertThat(object.getString("published_at")).isEqualTo("2024-06-01");
        assertThat(object.getJSONObject("author").getBooleanValue("active")).isTrue();
    }

    @Test
    void convertsGenericCollectionsAndJSONObjectAccessors() {
        String json = """
                [
                  {"id":1,"title":"A","price":12.50,"published_at":"2024-01-10","tags":["fast"]},
                  {"id":2,"title":"B","price":15.75,"published_at":"2024-02-20","tags":["json","binary"]}
                ]
                """;
        TypeReference<List<Book>> booksType = new TypeReference<List<Book>>() {
        };

        List<Book> books = JSON.parseObject(json, booksType);

        assertThat(books).hasSize(2);
        assertThat(books.get(0).getPublishedAt()).isEqualTo(LocalDate.of(2024, 1, 10));
        assertThat(books.get(1).getTags()).containsExactly("json", "binary");

        JSONObject catalog = JSONObject.of("name", "samples");
        JSONArray items = catalog.putArray("items");
        items.add(JSONObject.from(books.get(0)));
        items.add(JSONObject.from(books.get(1)));

        assertThat(catalog.getString("name")).isEqualTo("samples");
        assertThat(catalog.getJSONArray("items").getJSONObject(1).getString("title")).isEqualTo("B");
        assertThat(catalog.getJSONArray("items").getJSONObject(0).getBigDecimal("price"))
                .isEqualByComparingTo(new BigDecimal("12.50"));
    }

    @Test
    void evaluatesMutatesAndRemovesValuesWithJSONPath() {
        JSONObject document = JSON.parseObject("""
                {
                  "store": {
                    "books": [
                      {"title":"A","price":10},
                      {"title":"B","price":20}
                    ],
                    "open": true
                  }
                }
                """);

        assertThat(JSONPath.eval(document, "$.store.books[1].title")).isEqualTo("B");
        assertThat(JSONPath.contains(document, "$.store.open")).isTrue();

        JSONPath.set(document, "$.store.books[0].price", 11);
        JSONPath.remove(document, "$.store.open");

        assertThat(JSONPath.eval(document, "$.store.books[0].price")).isEqualTo(11);
        assertThat(JSONPath.contains(document, "$.store.open")).isFalse();
    }

    @Test
    void roundTripsJsonbForBeanAndContainerValues() {
        Book book = sampleBook();
        byte[] bytes = JSONB.toBytes(book, JSONWriter.Feature.WriteNulls);

        Book restored = JSONB.parseObject(bytes, Book.class);

        assertThat(restored.getTitle()).isEqualTo(book.getTitle());
        assertThat(restored.getAuthor().getName()).isEqualTo(book.getAuthor().getName());
        assertThat(restored.getPublishedAt()).isEqualTo(book.getPublishedAt());
        assertThat(restored.getTags()).containsExactlyElementsOf(book.getTags());

        byte[] arrayBytes = JSONB.toBytes(JSONArray.of("alpha", 7, true));
        JSONArray restoredArray = JSONB.parseArray(arrayBytes);
        assertThat(restoredArray.getString(0)).isEqualTo("alpha");
        assertThat(restoredArray.getIntValue(1)).isEqualTo(7);
        assertThat(restoredArray.getBooleanValue(2)).isTrue();
    }

    @Test
    void validatesInputAndAppliesWriterFilters() {
        byte[] validJson = "{\"name\":\"fastjson2\",\"features\":[\"json\",\"jsonb\"]}"
                .getBytes(StandardCharsets.UTF_8);
        JSONValidator validator = JSONValidator.fromUtf8(validJson);

        assertThat(validator.validate()).isTrue();
        assertThat(validator.getType()).isEqualTo(JSONValidator.Type.Object);
        assertThat(JSON.isValidArray("[1,2,3]")).isTrue();
        assertThat(JSON.isValidObject("[1,2,3]")).isFalse();

        Book book = sampleBook();
        book.setSummary(null);
        ValueFilter nullTextFilter = (object, name, value) -> value == null ? "unknown" : value;

        String filtered = JSON.toJSONString(book, nullTextFilter, JSONWriter.Feature.WriteNulls);
        JSONObject object = JSON.parseObject(filtered);
        assertThat(object.getString("summary")).isEqualTo("unknown");
        assertThat(object.getString("title")).isEqualTo(book.getTitle());
    }

    @Test
    void streamsJsonThroughReaderAndWriterApis() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JSONWriter writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("name");
            writer.writeColon();
            writer.writeString("streaming");
            writer.writeName("quantities");
            writer.writeColon();
            writer.startArray();
            writer.writeInt32(3);
            writer.writeComma();
            writer.writeInt32(5);
            writer.endArray();
            writer.writeName("active");
            writer.writeColon();
            writer.writeBool(true);
            writer.endObject();

            assertThat(writer.flushTo(output)).isPositive();
        }

        try (JSONReader reader = JSONReader.of(
                new ByteArrayInputStream(output.toByteArray()), StandardCharsets.UTF_8)) {
            Map<String, Object> document = reader.readObject();

            assertThat(document.get("name")).isEqualTo("streaming");
            assertThat(document.get("quantities")).isEqualTo(List.of(3, 5));
            assertThat(document.get("active")).isEqualTo(Boolean.TRUE);
        }
    }

    @Test
    void validatesDocumentsAgainstJsonSchemaConstraints() {
        JSONSchema schema = JSONSchema.parseSchema("""
                {
                  "type": "object",
                  "required": ["sku", "quantity", "status", "tags"],
                  "properties": {
                    "sku": {"type": "string", "minLength": 3, "pattern": "^[A-Z]{3}-\\\\d{3}$"},
                    "quantity": {"type": "integer", "minimum": 1, "maximum": 99},
                    "status": {"type": "string", "enum": ["draft", "active", "archived"]},
                    "tags": {
                      "type": "array",
                      "minItems": 1,
                      "uniqueItems": true,
                      "items": {"type": "string", "minLength": 2}
                    }
                  }
                }
                """);

        JSONObject validInventoryItem = JSON.parseObject("""
                {
                  "sku": "ABC-123",
                  "quantity": 12,
                  "status": "active",
                  "tags": ["warehouse", "fragile"]
                }
                """);
        JSONObject invalidInventoryItem = JSON.parseObject("""
                {
                  "sku": "bad",
                  "quantity": 0,
                  "status": "retired",
                  "tags": ["x", "x"]
                }
                """);

        ValidateResult validResult = schema.validate(validInventoryItem);
        ValidateResult invalidResult = schema.validate(invalidInventoryItem);

        assertThat(schema.getType()).isEqualTo(JSONSchema.Type.Object);
        assertThat(validResult.isSuccess()).isTrue();
        assertThat(schema.isValid(validInventoryItem)).isTrue();
        assertThat(invalidResult.isSuccess()).isFalse();
        assertThat(schema.isValid(invalidInventoryItem)).isFalse();
    }

    private static Book sampleBook() {
        Author author = new Author();
        author.setName("Grace");
        author.setActive(true);

        Book book = new Book();
        book.setId(7L);
        book.setTitle("Fast JSON");
        book.setPrice(new BigDecimal("28.25"));
        book.setPublishedAt(LocalDate.of(2024, 3, 5));
        book.setAvailable(true);
        book.setTags(List.of("json", "native-image"));
        book.setAuthor(author);
        book.setSummary("Integration coverage");
        return book;
    }

    public static class Book {
        private long id;
        private String title;
        private BigDecimal price;
        private LocalDate publishedAt;
        private boolean available;
        private List<String> tags;
        private Author author;
        private String summary;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        @JSONField(name = "title", alternateNames = {"book_title"})
        public void setTitle(String title) {
            this.title = title;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        @JSONField(name = "published_at", format = "yyyy-MM-dd")
        public LocalDate getPublishedAt() {
            return publishedAt;
        }

        @JSONField(name = "published_at", format = "yyyy-MM-dd")
        public void setPublishedAt(LocalDate publishedAt) {
            this.publishedAt = publishedAt;
        }

        public boolean isAvailable() {
            return available;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public Author getAuthor() {
            return author;
        }

        public void setAuthor(Author author) {
            this.author = author;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }
    }

    public static class Author {
        private String name;
        private boolean active;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
