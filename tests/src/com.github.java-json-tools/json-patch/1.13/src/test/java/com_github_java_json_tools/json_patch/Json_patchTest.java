/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_java_json_tools.json_patch;

import java.io.IOException;
import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonpatch.AddOperation;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.JsonPatchOperation;
import com.github.fge.jsonpatch.ReplaceOperation;
import com.github.fge.jsonpatch.TestOperation;
import com.github.fge.jsonpatch.diff.JsonDiff;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Json_patchTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.instance;

    @Test
    void appliesRfc6902PatchWithAllOperationTypesAndEscapedPointers() throws Exception {
        JsonNode source = json("""
                {
                  "name": "original",
                  "foo": ["bar", "quux"],
                  "nested": {
                    "a/b": 7,
                    "t~n": true
                  }
                }
                """);
        JsonNode originalSource = source.deepCopy();
        JsonPatch patch = JsonPatch.fromJson(json("""
                [
                  {"op": "add", "path": "/baz", "value": "qux"},
                  {"op": "add", "path": "/foo/-", "value": "tail"},
                  {"op": "replace", "path": "/name", "value": "updated"},
                  {"op": "copy", "from": "/nested/a~1b", "path": "/copied"},
                  {"op": "move", "from": "/foo/1", "path": "/moved"},
                  {"op": "remove", "path": "/nested/t~0n"},
                  {"op": "test", "path": "/copied", "value": 7}
                ]
                """));

        JsonNode patched = patch.apply(source);

        assertThat(patched).isEqualTo(json("""
                {
                  "name": "updated",
                  "foo": ["bar", "tail"],
                  "nested": {
                    "a/b": 7
                  },
                  "baz": "qux",
                  "copied": 7,
                  "moved": "quux"
                }
                """));
        assertThat(source).isEqualTo(originalSource);
        assertThat(patched).isNotSameAs(source);
    }

    @Test
    void composesPatchOperationsProgrammatically() throws Exception {
        JsonNode source = json("""
                {
                  "inventory": [
                    {"sku": "A-1", "count": 2, "tags": []},
                    {"sku": "B-2", "count": 8, "tags": ["clearance"]}
                  ]
                }
                """);
        JsonPatch patch = new JsonPatch(Arrays.<JsonPatchOperation>asList(
                new AddOperation(new JsonPointer("/inventory/1/tags/-"), NODE_FACTORY.textNode("featured")),
                new ReplaceOperation(new JsonPointer("/inventory/0/count"), NODE_FACTORY.numberNode(4)),
                new TestOperation(new JsonPointer("/inventory/1/tags/0"), NODE_FACTORY.textNode("clearance"))
        ));

        JsonNode patched = patch.apply(source);

        assertThat(patched).isEqualTo(json("""
                {
                  "inventory": [
                    {"sku": "A-1", "count": 4, "tags": []},
                    {"sku": "B-2", "count": 8, "tags": ["clearance", "featured"]}
                  ]
                }
                """));
    }

    @Test
    void computesDiffsThatTransformSourceIntoTarget() throws Exception {
        JsonNode source = json("""
                {
                  "id": 1,
                  "name": "old",
                  "items": ["a", "b", "c"],
                  "obsolete": true,
                  "nested": {
                    "flag": false
                  }
                }
                """);
        JsonNode target = json("""
                {
                  "id": 1,
                  "name": "new",
                  "items": ["a", "c", "d"],
                  "nested": {
                    "flag": true
                  },
                  "added": {
                    "x": 1
                  }
                }
                """);

        JsonNode diffJson = JsonDiff.asJson(source, target);
        JsonPatch patchFromJson = JsonPatch.fromJson(diffJson);
        JsonPatch patch = JsonDiff.asJsonPatch(source, target);

        assertThat(diffJson).isNotEmpty();
        assertThat(patchFromJson.apply(source)).isEqualTo(target);
        assertThat(patch.apply(source)).isEqualTo(target);
    }

    @Test
    void appliesJsonMergePatchToObjectsAndNonObjectTargets() throws Exception {
        JsonNode source = json("""
                {
                  "title": "Goodbye!",
                  "author": {
                    "givenName": "John",
                    "familyName": "Doe"
                  },
                  "tags": ["sample", "draft"],
                  "content": "This will be removed"
                }
                """);
        JsonMergePatch mergePatch = JsonMergePatch.fromJson(json("""
                {
                  "title": "Hello!",
                  "author": {
                    "familyName": null,
                    "givenName": "John"
                  },
                  "tags": ["example"],
                  "content": null,
                  "metadata": {
                    "reviewed": true
                  }
                }
                """));

        JsonNode patched = mergePatch.apply(source);
        JsonNode replacement = JsonMergePatch.fromJson(json("\"replacement\"")).apply(source);

        assertThat(patched).isEqualTo(json("""
                {
                  "title": "Hello!",
                  "author": {
                    "givenName": "John"
                  },
                  "tags": ["example"],
                  "metadata": {
                    "reviewed": true
                  }
                }
                """));
        assertThat(replacement).isEqualTo(NODE_FACTORY.textNode("replacement"));
    }

    @Test
    void appliesJsonPatchOperationsAtDocumentRoot() throws Exception {
        JsonNode source = json("""
                {
                  "status": "draft",
                  "items": ["old"]
                }
                """);
        JsonPatch addAtRoot = JsonPatch.fromJson(json("""
                [
                  {"op": "add", "path": "", "value": ["replacement"]}
                ]
                """));
        JsonPatch replaceAtRoot = JsonPatch.fromJson(json("""
                [
                  {"op": "replace", "path": "", "value": {"status": "published"}}
                ]
                """));
        JsonPatch removeAtRoot = JsonPatch.fromJson(json("""
                [
                  {"op": "remove", "path": ""}
                ]
                """));

        JsonNode added = addAtRoot.apply(source);
        JsonNode replaced = replaceAtRoot.apply(source);
        JsonNode removed = removeAtRoot.apply(source);

        assertThat(added).isEqualTo(json("""
                ["replacement"]
                """));
        assertThat(replaced).isEqualTo(json("""
                {"status": "published"}
                """));
        assertThat(removed.isMissingNode()).isTrue();
        assertThat(source).isEqualTo(json("""
                {
                  "status": "draft",
                  "items": ["old"]
                }
                """));
    }

    @Test
    void testOperationComparesJsonNumbersByNumericValue() throws Exception {
        JsonNode source = json("""
                {
                  "threshold": 1,
                  "items": [
                    {"price": 2.00}
                  ],
                  "status": "pending"
                }
                """);
        JsonPatch patch = JsonPatch.fromJson(json("""
                [
                  {"op": "test", "path": "/threshold", "value": 1.0},
                  {"op": "test", "path": "/items/0/price", "value": 2},
                  {"op": "replace", "path": "/status", "value": "accepted"}
                ]
                """));

        JsonNode patched = patch.apply(source);

        assertThat(patched).isEqualTo(json("""
                {
                  "threshold": 1,
                  "items": [
                    {"price": 2.00}
                  ],
                  "status": "accepted"
                }
                """));
    }

    @Test
    void reportsInvalidPatchDocumentsAndFailedOperations() throws Exception {
        JsonPatch failingTest = JsonPatch.fromJson(json("""
                [
                  {"op": "test", "path": "/enabled", "value": false}
                ]
                """));
        JsonPatch removingMissingValue = JsonPatch.fromJson(json("""
                [
                  {"op": "remove", "path": "/missing"}
                ]
                """));

        assertThatExceptionOfType(JsonPatchException.class)
                .isThrownBy(() -> failingTest.apply(json("""
                        {"enabled": true}
                        """)));
        assertThatExceptionOfType(JsonPatchException.class)
                .isThrownBy(() -> removingMissingValue.apply(json("{}")));
        assertThatThrownBy(() -> JsonPatch.fromJson(json("""
                [
                  {"op": "unknown", "path": "/enabled"}
                ]
                """)))
                .isInstanceOf(IOException.class);
    }

    private static JsonNode json(String content) throws IOException {
        return MAPPER.readTree(content);
    }
}
