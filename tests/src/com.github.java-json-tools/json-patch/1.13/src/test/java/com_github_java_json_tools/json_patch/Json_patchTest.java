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
import com.github.fge.jackson.jsonpointer.JsonPointerException;
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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Json_patchTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void patchDocumentAppliesAllRfc6902Operations() throws Exception {
        JsonNode source = json("""
                {
                  "foo": "bar",
                  "numbers": [1, 2, 3],
                  "nested": { "old": "value" },
                  "source": { "name": "native" }
                }
                """);
        JsonNode patchJson = json("""
                [
                  { "op": "test", "path": "/foo", "value": "bar" },
                  { "op": "replace", "path": "/foo", "value": "baz" },
                  { "op": "remove", "path": "/nested/old" },
                  { "op": "add", "path": "/nested/new", "value": true },
                  { "op": "add", "path": "/numbers/-", "value": 4 },
                  { "op": "copy", "from": "/source/name", "path": "/copied" },
                  { "op": "move", "from": "/copied", "path": "/moved" }
                ]
                """);

        JsonNode patched = JsonPatch.fromJson(patchJson).apply(source);

        assertThat(patched).isEqualTo(json("""
                {
                  "foo": "baz",
                  "numbers": [1, 2, 3, 4],
                  "nested": { "new": true },
                  "source": { "name": "native" },
                  "moved": "native"
                }
                """));
        assertThat(source).isEqualTo(json("""
                {
                  "foo": "bar",
                  "numbers": [1, 2, 3],
                  "nested": { "old": "value" },
                  "source": { "name": "native" }
                }
                """));
    }

    @Test
    public void operationConstructorsComposePatchAndSerializeAsPatchDocument() throws Exception {
        List<JsonPatchOperation> operations = Arrays.asList(
                new TestOperation(pointer("/status"), json("\"draft\"")),
                new AddOperation(pointer("/items/-"), json("{ \"name\": \"gamma\" }")),
                new ReplaceOperation(pointer("/status"), json("\"published\"")),
                new CopyOperation(pointer("/items/0/name"), pointer("/firstName")),
                new MoveOperation(pointer("/temporary"), pointer("/archived")),
                new RemoveOperation(pointer("/items/1"))
        );
        JsonPatch patch = new JsonPatch(operations);
        JsonNode source = json("""
                {
                  "items": [ { "name": "alpha" }, { "name": "beta" } ],
                  "status": "draft",
                  "temporary": true
                }
                """);

        JsonNode patched = patch.apply(source);
        JsonNode serializedPatch = json(MAPPER.writeValueAsString(patch));

        assertThat(patched).isEqualTo(json("""
                {
                  "items": [ { "name": "alpha" }, { "name": "gamma" } ],
                  "status": "published",
                  "firstName": "alpha",
                  "archived": true
                }
                """));
        assertThat(serializedPatch).isEqualTo(json("""
                [
                  { "op": "test", "path": "/status", "value": "draft" },
                  { "op": "add", "path": "/items/-", "value": { "name": "gamma" } },
                  { "op": "replace", "path": "/status", "value": "published" },
                  { "op": "copy", "path": "/firstName", "from": "/items/0/name" },
                  { "op": "move", "path": "/archived", "from": "/temporary" },
                  { "op": "remove", "path": "/items/1" }
                ]
                """));
        assertThat(patch.toString())
                .contains("op: test")
                .contains("op: add")
                .contains("op: replace")
                .contains("op: copy")
                .contains("op: move")
                .contains("op: remove");
    }

    @Test
    public void diffOutputCanBeAppliedAsJsonNodeOrJsonPatch() throws Exception {
        JsonNode source = json("""
                {
                  "name": "before",
                  "numbers": [1, 2],
                  "flags": { "enabled": true },
                  "obsolete": "remove"
                }
                """);
        JsonNode target = json("""
                {
                  "name": "after",
                  "numbers": [2, 3],
                  "flags": { "enabled": true, "new": false },
                  "added": "value"
                }
                """);

        JsonNode diffJson = JsonDiff.asJson(source, target);
        JsonNode appliedFromJson = JsonPatch.fromJson(diffJson).apply(source);
        JsonNode appliedFromPatch = JsonDiff.asJsonPatch(source, target).apply(source);

        assertThat(diffJson.size()).isGreaterThan(0);
        assertThat(diffJson.findValuesAsText("op"))
                .containsAnyOf("add", "remove", "replace", "move", "copy");
        assertThat(appliedFromJson).isEqualTo(target);
        assertThat(appliedFromPatch).isEqualTo(target);
    }

    @Test
    public void comparisonsTreatEquivalentJsonNumbersAsEqual() throws Exception {
        JsonNode source = json("""
                {
                  "integer": 1,
                  "decimal": 1.0,
                  "array": [2]
                }
                """);
        JsonNode patchJson = json("""
                [
                  { "op": "test", "path": "/integer", "value": 1.0 },
                  { "op": "test", "path": "/decimal", "value": 1 },
                  { "op": "test", "path": "/array/0", "value": 2.0 }
                ]
                """);
        JsonNode diffJson = JsonDiff.asJson(json("{ \"value\": 1 }"), json("{ \"value\": 1.0 }"));

        JsonNode patched = JsonPatch.fromJson(patchJson).apply(source);

        assertThat(patched).isEqualTo(source);
        assertThat(diffJson.size()).isZero();
    }

    @Test
    public void patchPathsUseJsonPointerEscapingForSpecialObjectMemberNames() throws Exception {
        JsonNode source = json("""
                {
                  "a/b": { "tilde~key": "old" },
                  "copy/from": "source value",
                  "regular": true
                }
                """);
        JsonNode patchJson = json("""
                [
                  { "op": "test", "path": "/a~1b/tilde~0key", "value": "old" },
                  { "op": "replace", "path": "/a~1b/tilde~0key", "value": "new" },
                  { "op": "copy", "from": "/copy~1from", "path": "/a~1b/copied~0value" },
                  { "op": "remove", "path": "/copy~1from" }
                ]
                """);

        JsonNode patched = JsonPatch.fromJson(patchJson).apply(source);

        assertThat(patched).isEqualTo(json("""
                {
                  "a/b": {
                    "tilde~key": "new",
                    "copied~value": "source value"
                  },
                  "regular": true
                }
                """));
    }

    @Test
    public void mergePatchUpdatesObjectsAndDeletesNullMembers() throws Exception {
        JsonNode source = json("""
                {
                  "title": "Goodbye!",
                  "author": {
                    "givenName": "John",
                    "familyName": "Doe"
                  },
                  "tags": ["example", "sample"],
                  "content": "unchanged"
                }
                """);
        JsonNode patchJson = json("""
                {
                  "title": "Hello!",
                  "phoneNumber": "+01-123-456-7890",
                  "author": { "familyName": null },
                  "tags": ["example"]
                }
                """);

        JsonMergePatch patch = JsonMergePatch.fromJson(patchJson);
        JsonNode patched = patch.apply(source);
        JsonNode serializedPatch = json(MAPPER.writeValueAsString(patch));

        assertThat(patched).isEqualTo(json("""
                {
                  "title": "Hello!",
                  "author": { "givenName": "John" },
                  "tags": ["example"],
                  "content": "unchanged",
                  "phoneNumber": "+01-123-456-7890"
                }
                """));
        assertThat(JsonMergePatch.fromJson(serializedPatch).apply(source)).isEqualTo(patched);
    }

    @Test
    public void nonObjectMergePatchReplacesTheWholeDocument() throws Exception {
        JsonNode source = json("""
                {
                  "title": "document",
                  "metadata": { "kept": false }
                }
                """);

        JsonNode patchedWithScalar = JsonMergePatch.fromJson(json("\"replacement\""))
                .apply(source);
        JsonNode patchedWithArray = JsonMergePatch.fromJson(json("[1, 2, 3]"))
                .apply(source);

        assertThat(patchedWithScalar).isEqualTo(json("\"replacement\""));
        assertThat(patchedWithArray).isEqualTo(json("[1, 2, 3]"));
    }

    @Test
    public void invalidPatchDocumentsAndFailedOperationsRaisePatchExceptions() throws Exception {
        assertThatThrownBy(() -> JsonPatch.fromJson(json("{ \"op\": \"add\" }")))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> JsonPatch.fromJson(json("""
                [ { "op": "add", "path": "/missing/child", "value": 1 } ]
                """)).apply(json("{}")))
                .isInstanceOf(JsonPatchException.class);
        assertThatThrownBy(() -> JsonPatch.fromJson(json("""
                [ { "op": "test", "path": "/status", "value": "published" } ]
                """)).apply(json("{ \"status\": \"draft\" }")))
                .isInstanceOf(JsonPatchException.class);
        assertThatThrownBy(() -> new RemoveOperation(pointer("/missing"))
                .apply(json("{ \"present\": true }")))
                .isInstanceOf(JsonPatchException.class);
    }

    private static JsonPointer pointer(String path) throws JsonPointerException {
        return new JsonPointer(path);
    }

    private static JsonNode json(String value) throws IOException {
        return MAPPER.readTree(value);
    }
}
