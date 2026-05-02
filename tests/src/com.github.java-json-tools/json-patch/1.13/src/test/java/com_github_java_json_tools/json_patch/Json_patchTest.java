/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_java_json_tools.json_patch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonpatch.AddOperation;
import com.github.fge.jsonpatch.CopyOperation;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.JsonPatchOperation;
import com.github.fge.jsonpatch.MoveOperation;
import com.github.fge.jsonpatch.RemoveOperation;
import com.github.fge.jsonpatch.ReplaceOperation;
import com.github.fge.jsonpatch.TestOperation;
import com.github.fge.jsonpatch.diff.JsonDiff;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Json_patchTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void appliesRfc6902PatchFromJson() throws Exception {
        JsonNode source = json("""
                {
                  "baz": "qux",
                  "foo": "bar",
                  "numbers": [1, 2, 3],
                  "object": { "nested": true },
                  "escaped": { "a/b": 1, "m~n": 2 }
                }
                """);
        JsonNode patchJson = json("""
                [
                  { "op": "test", "path": "/foo", "value": "bar" },
                  { "op": "replace", "path": "/baz", "value": "boo" },
                  { "op": "add", "path": "/hello", "value": ["world"] },
                  { "op": "add", "path": "/numbers/1", "value": 10 },
                  { "op": "copy", "from": "/object/nested", "path": "/copied" },
                  { "op": "move", "from": "/hello/0", "path": "/message" },
                  { "op": "remove", "path": "/foo" },
                  { "op": "replace", "path": "/escaped/a~1b", "value": 99 },
                  { "op": "add", "path": "/escaped/m~0n", "value": 42 }
                ]
                """);

        JsonPatch patch = JsonPatch.fromJson(patchJson);

        assertThat(patch.apply(source)).isEqualTo(json("""
                {
                  "baz": "boo",
                  "numbers": [1, 10, 2, 3],
                  "object": { "nested": true },
                  "escaped": { "a/b": 99, "m~n": 42 },
                  "hello": [],
                  "copied": true,
                  "message": "world"
                }
                """));
        JsonNode serializedPatch = MAPPER.valueToTree(patch);
        assertThat(serializedPatch).isEqualTo(patchJson);
    }

    @Test
    public void failedTestOperationAbortsPatchAndLeavesInputUnchanged() throws Exception {
        JsonNode source = json("""
                {
                  "version": 1,
                  "items": ["stable"]
                }
                """);
        JsonNode patchJson = json("""
                [
                  { "op": "test", "path": "/version", "value": 2 },
                  { "op": "add", "path": "/items/-", "value": "should-not-appear" }
                ]
                """);
        JsonPatch patch = JsonPatch.fromJson(patchJson);

        assertThatThrownBy(() -> patch.apply(source))
                .isInstanceOf(JsonPatchException.class)
                .hasMessageContaining("value differs");
        assertThat(source).isEqualTo(json("""
                {
                  "version": 1,
                  "items": ["stable"]
                }
                """));
    }

    @Test
    public void diffProducesPatchThatTransformsSourceIntoTarget() throws Exception {
        JsonNode source = json("""
                {
                  "name": "old",
                  "active": true,
                  "tags": ["red", "green"],
                  "details": {
                    "count": 1,
                    "obsolete": "remove"
                  }
                }
                """);
        JsonNode target = json("""
                {
                  "name": "new",
                  "active": true,
                  "tags": ["blue", "green", "gold"],
                  "details": {
                    "count": 2
                  },
                  "created": "today"
                }
                """);

        JsonPatch patch = JsonDiff.asJsonPatch(source, target);
        JsonNode patchJson = JsonDiff.asJson(source, target);

        assertThat(patch.apply(source)).isEqualTo(target);
        assertThat(JsonPatch.fromJson(patchJson).apply(source)).isEqualTo(target);
        for (JsonNode operation : patchJson) {
            assertThat(operation.hasNonNull("op")).isTrue();
            assertThat(operation.hasNonNull("path")).isTrue();
        }
    }

    @Test
    public void mergePatchUpdatesObjectsDeletesNullMembersAndReplacesArrays() throws Exception {
        JsonNode source = json("""
                {
                  "title": "Goodbye!",
                  "author": {
                    "givenName": "John",
                    "familyName": "Doe"
                  },
                  "tags": ["example", "sample"],
                  "content": "This will be unchanged"
                }
                """);
        JsonNode patchJson = json("""
                {
                  "title": "Hello!",
                  "phoneNumber": "+01-123-456-7890",
                  "author": {
                    "familyName": null
                  },
                  "tags": ["example"]
                }
                """);
        JsonMergePatch patch = JsonMergePatch.fromJson(patchJson);

        assertThat(patch.apply(source)).isEqualTo(json("""
                {
                  "title": "Hello!",
                  "author": {
                    "givenName": "John"
                  },
                  "tags": ["example"],
                  "content": "This will be unchanged",
                  "phoneNumber": "+01-123-456-7890"
                }
                """));
        JsonNode serializedPatch = MAPPER.valueToTree(patch);
        assertThat(serializedPatch).isEqualTo(patchJson);
    }

    @Test
    public void nonObjectMergePatchReplacesWholeDocument() throws Exception {
        JsonMergePatch scalarPatch = JsonMergePatch.fromJson(json("\"replacement\""));
        JsonMergePatch nullPatch = JsonMergePatch.fromJson(json("null"));

        assertThat(scalarPatch.apply(json("{ \"key\": \"value\" }"))).isEqualTo(json("\"replacement\""));
        assertThat(nullPatch.apply(json("[1, 2, 3]"))).isEqualTo(json("null"));
    }

    @Test
    public void programmaticOperationsApplyAndSerialize() throws Exception {
        JsonNode source = json("""
                {
                  "items": ["a", "b"],
                  "object": {
                    "name": "old",
                    "copy": "source"
                  }
                }
                """);
        List<JsonPatchOperation> operations = List.of(
                new AddOperation(pointer("/items/-"), json("\"c\"")),
                new ReplaceOperation(pointer("/object/name"), json("\"new\"")),
                new CopyOperation(pointer("/object/copy"), pointer("/object/copied")),
                new MoveOperation(pointer("/items/0"), pointer("/first")),
                new TestOperation(pointer("/object/copied"), json("\"source\"")),
                new RemoveOperation(pointer("/object/copy"))
        );
        JsonPatch patch = new JsonPatch(operations);

        assertThat(patch.apply(source)).isEqualTo(json("""
                {
                  "items": ["b", "c"],
                  "object": {
                    "name": "new",
                    "copied": "source"
                  },
                  "first": "a"
                }
                """));

        JsonNode serialized = MAPPER.valueToTree(patch);
        assertThat(serialized.get(0).get("op").textValue()).isEqualTo("add");
        assertThat(serialized.get(1).get("op").textValue()).isEqualTo("replace");
        assertThat(serialized.get(2).get("from").textValue()).isEqualTo("/object/copy");
        assertThat(serialized.get(3).get("op").textValue()).isEqualTo("move");
        assertThat(serialized.get(4).get("value")).isEqualTo(json("\"source\""));
        assertThat(serialized.get(5).get("path").textValue()).isEqualTo("/object/copy");
    }

    @Test
    public void removingMissingPathReportsPatchException() throws Exception {
        JsonPatch patch = JsonPatch.fromJson(json("""
                [
                  { "op": "remove", "path": "/missing" }
                ]
                """));

        assertThatThrownBy(() -> patch.apply(json("{ \"present\": true }")))
                .isInstanceOf(JsonPatchException.class)
                .hasMessageContaining("no such path");
    }

    private static JsonNode json(String value) throws IOException {
        return MAPPER.readTree(value);
    }

    private static JsonPointer pointer(String value) throws Exception {
        return new JsonPointer(value);
    }
}
