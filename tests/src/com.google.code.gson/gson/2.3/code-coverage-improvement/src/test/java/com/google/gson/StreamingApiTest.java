/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.gson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.internal.Streams;
import com.google.gson.internal.bind.JsonTreeReader;
import com.google.gson.internal.bind.JsonTreeWriter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StreamingApiTest {
    @Test
    void treeReaderWalksObjectsArraysAndPromotesNames() throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", true);
        root.addProperty("count", 4);
        root.addProperty("total", 900L);
        root.addProperty("name", "Ada");
        root.add("none", JsonNull.INSTANCE);
        JsonArray values = new JsonArray();
        values.add(new JsonPrimitive(1.5));
        values.add(new JsonPrimitive(false));
        root.add("values", values);
        root.add("ignored", new JsonObject());

        JsonTreeReader reader = new JsonTreeReader(root);
        assertThat(reader.peek()).isEqualTo(JsonToken.BEGIN_OBJECT);
        reader.beginObject();
        assertThat(reader.nextName()).isEqualTo("enabled");
        assertThat(reader.nextBoolean()).isTrue();
        assertThat(reader.nextName()).isEqualTo("count");
        assertThat(reader.nextInt()).isEqualTo(4);
        assertThat(reader.nextName()).isEqualTo("total");
        assertThat(reader.nextLong()).isEqualTo(900L);
        assertThat(reader.nextName()).isEqualTo("name");
        assertThat(reader.nextString()).isEqualTo("Ada");
        assertThat(reader.nextName()).isEqualTo("none");
        reader.nextNull();
        assertThat(reader.nextName()).isEqualTo("values");
        reader.beginArray();
        assertThat(reader.nextDouble()).isEqualTo(1.5d);
        assertThat(reader.nextBoolean()).isFalse();
        assertThat(reader.hasNext()).isFalse();
        reader.endArray();
        assertThat(reader.nextName()).isEqualTo("ignored");
        reader.skipValue();
        reader.endObject();
        assertThat(reader.peek()).isEqualTo(JsonToken.END_DOCUMENT);
        assertThat(reader.toString()).isEqualTo("JsonTreeReader");
        reader.close();
        assertThatThrownBy(reader::peek).isInstanceOf(IllegalStateException.class);

        JsonObject named = new JsonObject();
        named.addProperty("key", "value");
        JsonTreeReader promoted = new JsonTreeReader(named);
        promoted.beginObject();
        promoted.promoteNameToValue();
        assertThat(promoted.nextString()).isEqualTo("key");
        assertThat(promoted.nextString()).isEqualTo("value");
        promoted.endObject();
    }

    @Test
    void gsonReaderAcceptsLenientCommentsAndReportsMalformedDocuments() throws Exception {
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, Integer>>() { }.getType();
        JsonReader blockComment = new JsonReader(
                new StringReader("/* leading */{\"value\":1}"));
        blockComment.setLenient(true);
        Map<String, Integer> map = gson.fromJson(blockComment, mapType);
        assertThat(map).containsEntry("value", 1);

        JsonReader lineComment = new JsonReader(new StringReader("// leading comment\n[true]"));
        lineComment.setLenient(true);
        JsonArray lineCommentArray = gson.fromJson(lineComment, JsonArray.class);
        assertThat(lineCommentArray.get(0).getAsBoolean()).isTrue();

        JsonReader malformed = new JsonReader(new StringReader("{\"value\" 1}"));
        assertThatThrownBy(() -> gson.fromJson(malformed, Object.class))
                .isInstanceOf(JsonSyntaxException.class);
    }

    @Test
    void treeWriterBuildsJsonTreesAndHonorsNulls() throws Exception {
        JsonTreeWriter writer = new JsonTreeWriter();
        writer.beginObject().name("name").value("Ada").name("age").value(37L)
                .name("enabled").value(true).name("ratio").value(1.5d)
                .name("optional").nullValue().name("items").beginArray()
                .value("first").value(2L).value(3.5d).endArray().endObject();
        writer.flush();
        JsonObject result = writer.get().getAsJsonObject();
        assertThat(result.getAsJsonPrimitive("name").getAsString()).isEqualTo("Ada");
        assertThat(result.getAsJsonArray("items")).hasSize(3);
        assertThat(result.has("optional")).isTrue();
        writer.close();
        assertThatThrownBy(() -> writer.beginArray()).isInstanceOf(IllegalStateException.class);

        JsonTreeWriter omitted = new JsonTreeWriter();
        omitted.setSerializeNulls(false);
        omitted.beginObject().name("empty").nullValue().endObject();
        assertThat(omitted.get().getAsJsonObject().has("empty")).isFalse();
    }

    @Test
    void characterReadersAndWritersHandlePathsNumbersAndSkipping() throws Exception {
        JsonReader reader = new JsonReader(new StringReader(
                "{\"id\":9007199254740991,\"asText\":\"17\",\"unknown\":{\"a\":[1,2]},\"none\":null}"));
        assertThat(reader.toString()).contains("JsonReader at line");
        reader.beginObject();
        assertThat(reader.getPath()).isEqualTo("$.");
        assertThat(reader.nextName()).isEqualTo("id");
        assertThat(reader.nextLong()).isEqualTo(9007199254740991L);
        assertThat(reader.nextName()).isEqualTo("asText");
        assertThat(reader.nextLong()).isEqualTo(17L);
        reader.nextName();
        reader.skipValue();
        reader.nextName();
        reader.nextNull();
        reader.endObject();
        JsonReader decimalReader = new JsonReader(new StringReader("[1.25]"));
        decimalReader.beginArray();
        assertThat(decimalReader.nextDouble()).isEqualTo(1.25d);
        decimalReader.endArray();
        reader.close();
        assertThatThrownBy(reader::peek).isInstanceOf(IllegalStateException.class);

        StringWriter output = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(output);
        jsonWriter.setIndent("  ");
        jsonWriter.beginArray().value(5L).value(1.25d).nullValue().endArray();
        jsonWriter.flush();
        jsonWriter.close();
        assertThat(output.toString()).contains("1.25").contains("\n");
    }

    @Test
    void parsersAndStreamsReadMultipleDocumentsAndPreserveLeniency() throws Exception {
        JsonParser parser = new JsonParser();
        assertThat(parser.parse("{\"a\":1}").getAsJsonObject().get("a").getAsInt()).isEqualTo(1);
        JsonReader reader = new JsonReader(new StringReader("[true,false]"));
        JsonElement parsed = parser.parse(reader);
        assertThat(parsed.getAsJsonArray()).hasSize(2);
        assertThat(reader.isLenient()).isFalse();
        assertThat(parser.parse(new StringReader("\"text\""))).isEqualTo(new JsonPrimitive("text"));

        Iterator<JsonElement> stream = new JsonStreamParser("[1] {\"two\":2} true");
        assertThat(stream.hasNext()).isTrue();
        JsonElement first = stream.next();
        assertThat(first.getAsJsonArray().getAsInt()).isEqualTo(1);
        JsonElement second = stream.next();
        assertThat(second.getAsJsonObject().get("two").getAsInt()).isEqualTo(2);
        assertThat(stream.next()).isEqualTo(new JsonPrimitive(true));
        assertThat(stream.hasNext()).isFalse();
        assertThatThrownBy(stream::remove).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(stream::next).isInstanceOf(java.util.NoSuchElementException.class);

        JsonReader streamReader = new JsonReader(new StringReader("{\"stream\":true}"));
        JsonElement viaStreams = Streams.parse(streamReader);
        assertThat(viaStreams.getAsJsonObject().get("stream").getAsBoolean()).isTrue();
        StringWriter output = new StringWriter();
        Streams.write(viaStreams, new JsonWriter(output));
        assertThat(output.toString()).isEqualTo("{\"stream\":true}");
    }
}
