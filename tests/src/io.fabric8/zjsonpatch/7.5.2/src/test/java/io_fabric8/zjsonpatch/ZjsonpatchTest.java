/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.zjsonpatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.zjsonpatch.CompatibilityFlags;
import io.fabric8.zjsonpatch.DiffFlags;
import io.fabric8.zjsonpatch.JsonDiff;
import io.fabric8.zjsonpatch.JsonPatch;
import io.fabric8.zjsonpatch.JsonPatchException;
import io.fabric8.zjsonpatch.JsonPointer;
import io.fabric8.zjsonpatch.JsonPointerEvaluationException;
import io.fabric8.zjsonpatch.Operation;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ZjsonpatchTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void appliesPatchWithAllRfc6902OperationsWithoutMutatingSource() throws Exception {
        JsonNode source = json("""
                {
                  "name": "Ada",
                  "meta": {
                    "role": "developer",
                    "active": true
                  },
                  "phones": ["111", "222"],
                  "address": {
                    "city": "London"
                  },
                  "escaped/key": {
                    "~name": "original"
                  }
                }
                """);
        JsonNode originalSource = source.deepCopy();
        JsonNode patch = json("""
                [
                  {"op": "test", "path": "/meta/role", "value": "developer"},
                  {"op": "add", "path": "/phones/-", "value": "333"},
                  {"op": "copy", "from": "/address/city", "path": "/homeCity"},
                  {"op": "move", "from": "/meta/active", "path": "/enabled"},
                  {"op": "replace", "path": "/name", "value": "Ada Lovelace"},
                  {"op": "remove", "path": "/phones/0"},
                  {"op": "add", "path": "/escaped~1key/~0name", "value": "updated"}
                ]
                """);

        JsonNode result = JsonPatch.apply(patch, source);

        assertThat(result).isEqualTo(json("""
                {
                  "name": "Ada Lovelace",
                  "meta": {
                    "role": "developer"
                  },
                  "phones": ["222", "333"],
                  "address": {
                    "city": "London"
                  },
                  "escaped/key": {
                    "~name": "updated"
                  },
                  "homeCity": "London",
                  "enabled": true
                }
                """));
        assertThat(source).isEqualTo(originalSource);
    }

    @Test
    void generatedDiffPatchTransformsSourceIntoTargetAndIncludesRequestedDetails() throws Exception {
        JsonNode source = json("""
                {
                  "name": "old",
                  "stale": true,
                  "numbers": [1, 2, 3],
                  "nested": {
                    "same": "value"
                  }
                }
                """);
        JsonNode target = json("""
                {
                  "name": "new",
                  "numbers": [1, 3, 4],
                  "nested": {
                    "same": "value",
                    "added": "field"
                  }
                }
                """);
        EnumSet<DiffFlags> flags = EnumSet.of(
                DiffFlags.ADD_ORIGINAL_VALUE_ON_REPLACE,
                DiffFlags.EMIT_TEST_OPERATIONS,
                DiffFlags.OMIT_MOVE_OPERATION,
                DiffFlags.OMIT_COPY_OPERATION);

        JsonNode patch = JsonDiff.asJson(source, target, flags);
        JsonNode result = JsonPatch.apply(patch, source);

        assertThat(result).isEqualTo(target);
        assertThat(containsOperationAtPath(patch, Operation.TEST, "/name")).isTrue();
        JsonNode nameReplacement = operationAtPath(patch, Operation.REPLACE, "/name");
        assertThat(nameReplacement.get(JsonDiff.VALUE).asText()).isEqualTo("new");
        assertThat(nameReplacement.get(JsonDiff.FROM_VALUE).asText()).isEqualTo("old");
        assertThat(containsOperationAtPath(patch, Operation.REMOVE, "/stale")).isTrue();
        assertThat(containsOperationAtPath(patch, Operation.ADD, "/nested/added")).isTrue();
    }

    @Test
    void diffFlagsCanOmitRemoveValuesAndSplitReplacementsIntoRemoveAddPairs() throws Exception {
        JsonNode source = json("""
                {
                  "name": "old",
                  "removed": "value"
                }
                """);
        JsonNode target = json("""
                {
                  "name": "new"
                }
                """);
        EnumSet<DiffFlags> omitRemoveValueFlags = EnumSet.of(
                DiffFlags.OMIT_VALUE_ON_REMOVE,
                DiffFlags.OMIT_MOVE_OPERATION,
                DiffFlags.OMIT_COPY_OPERATION);
        JsonNode removeOmittingPatch = JsonDiff.asJson(source, target, omitRemoveValueFlags);

        assertThat(JsonPatch.apply(removeOmittingPatch, source)).isEqualTo(target);
        for (JsonNode operation : removeOmittingPatch) {
            if (operation.get(JsonDiff.OP).asText().equals(Operation.REMOVE.rfcName())) {
                assertThat(operation.has(JsonDiff.VALUE)).isFalse();
            }
        }

        EnumSet<DiffFlags> splitReplacementFlags = EnumSet.of(
                DiffFlags.ADD_EXPLICIT_REMOVE_ADD_ON_REPLACE,
                DiffFlags.ADD_ORIGINAL_VALUE_ON_REPLACE,
                DiffFlags.OMIT_MOVE_OPERATION,
                DiffFlags.OMIT_COPY_OPERATION);
        JsonNode replacementPatch = JsonDiff.asJson(
                json("""
                        {"name":"old"}
                        """),
                json("""
                        {"name":"new"}
                        """),
                splitReplacementFlags);

        assertThat(JsonPatch.apply(replacementPatch, json("""
                {"name":"old"}
                """)))
                .isEqualTo(json("""
                        {"name":"new"}
                        """));
        assertThat(containsOperationAtPath(replacementPatch, Operation.REMOVE, "/name")).isTrue();
        assertThat(containsOperationAtPath(replacementPatch, Operation.ADD, "/name")).isTrue();
        assertThat(containsOperationAtPath(replacementPatch, Operation.REPLACE, "/name")).isFalse();
    }

    @Test
    void compatibilityFlagsEnableDocumentedLenientPatchApplication() throws Exception {
        JsonNode source = json("""
                {
                  "nullable": null,
                  "array": ["first"],
                  "object": {}
                }
                """);
        JsonNode patch = json("""
                [
                  {"op": "test", "path": "/nullable"},
                  {"op": "add", "path": "/object/missingValue"},
                  {"op": "replace", "path": "/object/replaced"},
                  {"op": "remove", "path": "/array/5"}
                ]
                """);
        EnumSet<CompatibilityFlags> flags = EnumSet.of(
                CompatibilityFlags.MISSING_VALUES_AS_NULLS,
                CompatibilityFlags.ALLOW_MISSING_TARGET_OBJECT_ON_REPLACE,
                CompatibilityFlags.REMOVE_NONE_EXISTING_ARRAY_ELEMENT);

        JsonNode result = JsonPatch.apply(patch, source, flags);

        assertThat(result).isEqualTo(json("""
                {
                  "nullable": null,
                  "array": ["first"],
                  "object": {
                    "missingValue": null,
                    "replaced": null
                  }
                }
                """));
        assertThatThrownBy(() -> JsonPatch.apply(patch, source))
                .isInstanceOf(JsonPatchException.class)
                .hasMessageContaining("missing 'value' field");
    }

    @Test
    void jsonPointerParsesEscapedTokensAndReportsEvaluationFailures() throws Exception {
        JsonNode document = json("""
                {
                  "escaped/key": {
                    "~name": ["zero", "one"]
                  }
                }
                """);
        JsonPointer pointer = JsonPointer.parse("/escaped~1key/~0name/1");

        assertThat(pointer.isRoot()).isFalse();
        assertThat(pointer.toString()).isEqualTo("/escaped~1key/~0name/1");
        assertThat(pointer.evaluate(document).asText()).isEqualTo("one");
        assertThat(pointer.getParent().toString()).isEqualTo("/escaped~1key/~0name");
        List<?> tokens = pointer.decompose();
        assertThat(tokens).hasSize(3);
        assertThat(tokens.get(0).toString()).isEqualTo("escaped~1key");
        assertThat(tokens.get(2).toString()).isEqualTo("1");
        assertThat(JsonPointer.parse("").isRoot()).isTrue();
        assertThat(JsonPointer.ROOT.getParent()).isSameAs(JsonPointer.ROOT);

        assertThatThrownBy(() -> JsonPointer.parse("escaped/key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing leading slash");
        assertThatThrownBy(() -> JsonPointer.parse("/bad~2escape"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid escape sequence");
        assertThatThrownBy(() -> JsonPointer.parse("/escaped~1key/missing").evaluate(document))
                .isInstanceOf(JsonPointerEvaluationException.class)
                .satisfies(error -> {
                    JsonPointerEvaluationException evaluationException =
                            (JsonPointerEvaluationException) error;
                    assertThat(evaluationException.getPath().toString()).isEqualTo("/escaped~1key");
                    assertThat(evaluationException.getTarget()).isEqualTo(document);
                });
    }

    @Test
    void operationLookupAndPatchExceptionsExposeOperationAndPath() throws Exception {
        assertThat(Operation.fromRfcName("ADD")).isEqualTo(Operation.ADD);
        assertThat(Operation.MOVE.rfcName()).isEqualTo("move");
        assertThat(CompatibilityFlags.defaults()).isEmpty();
        assertThat(DiffFlags.defaults())
                .contains(DiffFlags.OMIT_VALUE_ON_REMOVE)
                .doesNotContain(DiffFlags.OMIT_MOVE_OPERATION);
        assertThat(DiffFlags.dontNormalizeOpIntoMoveAndCopy())
                .contains(DiffFlags.OMIT_MOVE_OPERATION, DiffFlags.OMIT_COPY_OPERATION);
        assertThatThrownBy(() -> Operation.fromRfcName(null))
                .isInstanceOf(JsonPatchException.class)
                .hasMessageContaining("rfcName cannot be null");

        JsonNode patch = json("""
                [
                  {"op": "remove", "path": "/object/missing"}
                ]
                """);
        JsonNode source = json("""
                {"object":{}}
                """);
        EnumSet<CompatibilityFlags> flags = EnumSet.of(
                CompatibilityFlags.MISSING_VALUES_AS_NULLS,
                CompatibilityFlags.FORBID_REMOVE_MISSING_OBJECT);

        assertThatThrownBy(() -> JsonPatch.apply(patch, source, flags))
                .isInstanceOf(JsonPatchException.class)
                .satisfies(error -> {
                    JsonPatchException patchException = (JsonPatchException) error;
                    assertThat(patchException.getOperation()).isEqualTo(Operation.REMOVE);
                    assertThat(patchException.getPath().toString()).isEqualTo("/object");
                    assertThat(patchException.toString()).contains("REMOVE", "/object");
                });
    }

    private static JsonNode json(String content) throws Exception {
        return MAPPER.readTree(content);
    }

    private static boolean containsOperationAtPath(JsonNode patch, Operation operation, String path) {
        for (JsonNode entry : patch) {
            if (entry.get(JsonDiff.OP).asText().equals(operation.rfcName())
                    && entry.get(JsonDiff.PATH).asText().equals(path)) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode operationAtPath(JsonNode patch, Operation operation, String path) {
        for (JsonNode entry : patch) {
            if (entry.get(JsonDiff.OP).asText().equals(operation.rfcName())
                    && entry.get(JsonDiff.PATH).asText().equals(path)) {
                return entry;
            }
        }
        throw new AssertionError("Missing " + operation.rfcName() + " operation at " + path + " in " + patch);
    }
}
