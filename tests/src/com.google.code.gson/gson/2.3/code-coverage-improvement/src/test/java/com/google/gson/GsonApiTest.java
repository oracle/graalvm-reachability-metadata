/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.gson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.Since;
import com.google.gson.annotations.Until;
import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class GsonApiTest {
    @Test
    void gsonSupportsTreeObjectReaderAndAppendableRoundTrips() throws Exception {
        Gson gson = new Gson();
        JsonObject tree = new JsonObject();
        tree.addProperty("message", "hello");
        tree.addProperty("count", 3);

        assertThat(gson.toJson(tree)).isEqualTo("{\"message\":\"hello\",\"count\":3}");
        StringBuilder treeOutput = new StringBuilder();
        gson.toJson(tree, treeOutput);
        assertThat(treeOutput.toString()).isEqualTo(gson.toJson(tree));
        StringWriter jsonWriterOutput = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(jsonWriterOutput);
        gson.toJson(tree, jsonWriter);
        assertThat(jsonWriterOutput.toString()).isEqualTo(gson.toJson(tree));

        Payload payload = gson.fromJson(tree, Payload.class);
        assertThat(payload.message).isEqualTo("hello");
        assertThat(((Payload) gson.fromJson(tree, (Type) Payload.class)).count).isEqualTo(3);
        Reader reader = new StringReader("{\"message\":\"read\",\"count\":4}");
        assertThat(gson.fromJson(reader, Payload.class).count).isEqualTo(4);

        assertThat(gson.toJsonTree(payload).getAsJsonObject().get("message").getAsString())
                .isEqualTo("hello");
        Type listType = new TypeToken<List<String>>() { }.getType();
        List<String> values = Arrays.asList("a", "b");
        JsonElement listTree = gson.toJsonTree(values, listType);
        List<String> decodedValues = gson.fromJson(listTree, listType);
        assertThat(decodedValues).containsExactly("a", "b");
        StringBuilder objectOutput = new StringBuilder();
        gson.toJson(values, objectOutput);
        List<String> decodedOutput = gson.fromJson(objectOutput.toString(), listType);
        assertThat(decodedOutput).containsExactly("a", "b");
        assertThat(gson.toJson((Object) null)).isEqualTo("null");
        assertThat(gson.fromJson((JsonElement) null, Payload.class)).isNull();
        assertThat(gson.fromJson((String) null, Payload.class)).isNull();
        assertThat(gson.toString()).contains("serializeNulls");
    }

    @Test
    void builderOptionsChangeObservableSerializationBehavior() {
        ExclusionStrategy skipSecret = new ExclusionStrategy() {
            @Override public boolean shouldSkipField(FieldAttributes field) {
                return field.getName().equals("secret");
            }
            @Override public boolean shouldSkipClass(Class<?> clazz) {
                return false;
            }
        };
        FieldNamingStrategy snakeCase = field -> field.getName().replace("message", "message_value");
        Gson gson = new GsonBuilder()
                .setVersion(1.0)
                .excludeFieldsWithModifiers(java.lang.reflect.Modifier.TRANSIENT)
                .excludeFieldsWithoutExposeAnnotation()
                .disableInnerClassSerialization()
                .setLongSerializationPolicy(LongSerializationPolicy.STRING)
                .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
                .setFieldNamingStrategy(snakeCase)
                .setExclusionStrategies(skipSecret)
                .addSerializationExclusionStrategy(skipSecret)
                .addDeserializationExclusionStrategy(skipSecret)
                .serializeNulls()
                .enableComplexMapKeySerialization()
                .generateNonExecutableJson()
                .disableHtmlEscaping()
                .setPrettyPrinting()
                .serializeSpecialFloatingPointValues()
                .setDateFormat("yyyy-MM-dd")
                .create();

        Configured configured = new Configured();
        String json = gson.toJson(configured);
        assertThat(json).startsWith(")]}'\n");
        assertThat(json).contains("message_value").doesNotContain("secret");
        assertThat(json).contains("<tag>");
        assertThat(gson.fromJson(json.substring(5), Configured.class).published).isEqualTo("yes");

        Gson dateStyle = new GsonBuilder().setDateFormat(java.text.DateFormat.SHORT).create();
        assertThat(dateStyle.toJson(new java.util.Date(0))).isNotEmpty();
        Gson dateTimeStyle = new GsonBuilder()
                .setDateFormat(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).create();
        assertThat(dateTimeStyle.toJson(new java.util.Date(0))).isNotEmpty();
    }

    @Test
    void adaptersFactoriesHierarchyAndDelegatesComposeWithGson() throws Exception {
        TypeAdapter<Payload> payloadAdapter = new TypeAdapter<Payload>() {
            @Override public void write(JsonWriter out, Payload value) throws java.io.IOException {
                out.value(value == null ? null : value.message.toUpperCase());
            }
            @Override public Payload read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                Payload payload = new Payload();
                payload.message = in.nextString();
                return payload;
            }
        };
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Payload.class, payloadAdapter)
                .registerTypeAdapterFactory(new TypeAdapterFactory() {
                    @Override public <T> TypeAdapter<T> create(Gson context, TypeToken<T> type) {
                        return null;
                    }
                })
                .registerTypeHierarchyAdapter(Number.class, new JsonSerializer<Number>() {
                    @Override public JsonElement serialize(Number source, Type typeOfSource,
                            JsonSerializationContext context) {
                        return new JsonPrimitive("number:" + source);
                    }
                })
                .create();
        assertThat(gson.toJson(new PayloadWithExpose())).contains("published");
        assertThat(gson.toJson(new Payload("small"))).isEqualTo("\"SMALL\"");
        assertThat(gson.fromJson("\"decoded\"", Payload.class).message).isEqualTo("decoded");
        assertThat(gson.getAdapter(Payload.class)).isNotNull();
        assertThat(gson.getAdapter(TypeToken.get(Payload.class))).isNotNull();

        TypeAdapterFactory delegateFactory = new TypeAdapterFactory() {
            @Override public <T> TypeAdapter<T> create(Gson context, TypeToken<T> type) {
                return context.getDelegateAdapter(this, type);
            }
        };
        Gson delegated = new GsonBuilder().registerTypeAdapterFactory(delegateFactory).create();
        assertThat(delegated.toJson(new Payload("delegated"))).contains("delegated");
        assertThat(delegated.getDelegateAdapter(delegateFactory, TypeToken.get(Payload.class)))
                .isNotNull();

        TypeAdapter<String[]> arrayAdapter = gson.getAdapter(String[].class);
        StringWriter output = new StringWriter();
        arrayAdapter.toJson(output, new String[] {"one", "two"});
        assertThat(output.toString()).isEqualTo("[\"one\",\"two\"]");
    }

    @Test
    void recursiveAdaptersAndDelegatingTreeAdaptersUseTheirPublicGsonRoutes() {
        Gson gson = new Gson();
        RecursiveNode node = new RecursiveNode();
        node.name = "root";
        node.child = new RecursiveNode();
        node.child.name = "leaf";
        String json = gson.toJson(node, RecursiveNode.class);
        assertThat(json).contains("root").contains("leaf");
        assertThat(gson.fromJson(json, RecursiveNode.class).child.name).isEqualTo("leaf");

        Gson serializerOnly = new GsonBuilder()
                .registerTypeAdapter(Payload.class, (JsonSerializer<Payload>) (source, type, context)
                        -> new JsonPrimitive(source.message.toUpperCase()))
                .create();
        assertThat(serializerOnly.fromJson("{\"message\":\"delegated\"}", Payload.class).message)
                .isEqualTo("delegated");

        Gson deserializerOnly = new GsonBuilder()
                .registerTypeAdapter(Payload.class,
                        (JsonDeserializer<Payload>) (element, type, context)
                                -> new Payload(element.getAsJsonObject().get("message").getAsString()))
                .create();
        assertThat(deserializerOnly.toJson(new Payload("delegated"), Payload.class))
                .contains("delegated");

        ExclusionStrategy skipPayload = new ExclusionStrategy() {
            @Override public boolean shouldSkipField(FieldAttributes field) {
                return false;
            }
            @Override public boolean shouldSkipClass(Class<?> clazz) {
                return clazz == Payload.class;
            }
        };
        Gson serializationExcluded = new GsonBuilder()
                .addSerializationExclusionStrategy(skipPayload).create();
        assertThat(serializationExcluded.fromJson("{\"message\":\"read\"}", Payload.class).message)
                .isEqualTo("read");
        Gson deserializationExcluded = new GsonBuilder()
                .addDeserializationExclusionStrategy(skipPayload).create();
        assertThat(deserializationExcluded.toJson(new Payload("written"), Payload.class))
                .contains("written");

        com.google.gson.stream.JsonReader skipped = new com.google.gson.stream.JsonReader(
                new StringReader("unquoted-value"));
        skipped.setLenient(true);
        Payload skippedPayload = deserializationExcluded.fromJson(skipped, Payload.class);
        assertThat(skippedPayload).isNull();
    }

    @Test
    void typedBooleanSerializationUsesConfiguredStringAdapterThroughGsonWriter() throws Exception {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Boolean.class, TypeAdapters.BOOLEAN_AS_STRING)
                .create();
        StringWriter output = new StringWriter();
        JsonWriter writer = new JsonWriter(output);
        gson.toJson(Boolean.TRUE, Boolean.class, writer);
        writer.flush();

        assertThat(output.toString()).isEqualTo("\"true\"");
        assertThat(gson.toJson(Boolean.FALSE, Boolean.class)).isEqualTo("\"false\"");
        assertThat(gson.toJson(null, Boolean.class)).isEqualTo("\"null\"");
    }

    @Test
    void primitiveGsonAdaptersRejectSpecialFloatingPointValues() {
        Gson gson = new Gson();
        assertThat(gson.fromJson("1.5", Double.class)).isEqualTo(1.5d);
        assertThat(gson.fromJson("1.5", Float.class)).isEqualTo(1.5f);
        assertThat(gson.fromJson("3.5", Number.class).doubleValue()).isEqualTo(3.5d);
        assertThat(gson.toJson(1.5d, Double.class)).isEqualTo("1.5");
        assertThat(gson.toJson(1.5f, Float.class)).isEqualTo("1.5");
        assertThat(gson.toJson(3.5d, Number.class)).isEqualTo("3.5");
        assertThatThrownBy(() -> gson.toJson(Double.NaN, Double.class))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gson.toJson(Float.POSITIVE_INFINITY, Float.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicNumberRoutesCoverConfiguredLongAndSpecialFloatingPointAdapters() {
        Gson stringLongs = new GsonBuilder()
                .setLongSerializationPolicy(LongSerializationPolicy.STRING).create();
        assertThat(stringLongs.fromJson("123", Long.class)).isEqualTo(123L);
        assertThat(stringLongs.fromJson("null", Long.class)).isNull();
        assertThat(stringLongs.toJson(123L, Long.class)).isEqualTo("\"123\"");
        assertThat(stringLongs.toJson(null, Long.class)).isEqualTo("null");

        Gson specialFloats = new GsonBuilder().serializeSpecialFloatingPointValues().create();
        assertThat(specialFloats.fromJson("1.25", Float.class)).isEqualTo(1.25f);
        assertThat(specialFloats.fromJson("2.5", Double.class)).isEqualTo(2.5d);
        assertThat(specialFloats.toJson(Float.NaN, Float.class)).isEqualTo("NaN");
        assertThat(specialFloats.toJson(Double.POSITIVE_INFINITY, Double.class))
                .isEqualTo("Infinity");
    }

    @Test
    void typeAdapterConvenienceMethodsAndNullSafetyAreUseful() throws Exception {
        TypeAdapter<Payload> adapter = new TypeAdapter<Payload>() {
            @Override public void write(JsonWriter out, Payload value) throws java.io.IOException {
                out.beginObject().name("message").value(value.message).endObject();
            }
            @Override public Payload read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                Payload value = new Payload();
                in.beginObject();
                in.nextName();
                value.message = in.nextString();
                in.endObject();
                return value;
            }
        };
        assertThat(adapter.toJson(new Payload("written")))
                .isEqualTo("{\"message\":\"written\"}");
        assertThat(adapter.fromJson("{\"message\":\"read\"}").message).isEqualTo("read");
        assertThat(adapter.toJsonTree(new Payload("tree")).getAsJsonObject()
                .get("message").getAsString()).isEqualTo("tree");
        JsonObject tree = new JsonObject();
        tree.addProperty("message", "tree-read");
        assertThat(adapter.fromJsonTree(tree).message).isEqualTo("tree-read");
        StringWriter writer = new StringWriter();
        adapter.toJson(writer, new Payload("writer"));
        assertThat(writer.toString()).isEqualTo("{\"message\":\"writer\"}");

        TypeAdapter<Payload> safe = adapter.nullSafe();
        assertThat(safe.toJsonTree(null)).isEqualTo(JsonNull.INSTANCE);
        assertThat(safe.fromJsonTree(JsonNull.INSTANCE)).isNull();
        assertThatThrownBy(() -> adapter.fromJsonTree(JsonNull.INSTANCE))
                .isInstanceOf(IllegalStateException.class);
    }

    static class Payload {
        String message;
        int count;
        Payload() {
        }
        Payload(String message) {
            this.message = message;
        }
    }

    static class RecursiveNode {
        String name;
        RecursiveNode child;
    }

    static class PayloadWithExpose {
        @Expose String published = "yes";
        String hidden = "no";
    }

    static class Configured {
        @Expose String message = "<tag>";
        @Expose String published = "yes";
        @Expose transient String transientValue = "omit";
        @Expose @Since(2.0) String future = "future";
        @Expose @Until(0.5) String old = "old";
        @Expose String secret = "secret";
    }
}
