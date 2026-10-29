/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Rewrites agent conditions that name a JVM hidden lambda class to the lambda's host class.
///
/// The conditional agent can record `Host$$Lambda/0x…` as the reached type (§graal/GR-80926).
/// A hidden class cannot be named, so the condition moves to `Host` (§FS-metadata.2).
public final class HiddenLambdaConditions {

    private static final List<String> CONDITION_KEYS = List.of("typeReached", "typeReachable");
    /// `Host$$Lambda`, then an optional `$<n>` counter and `/0x<hex>` (or older `/<n>`) suffix.
    private static final Pattern HIDDEN_LAMBDA = Pattern.compile("^(.+?)\\$\\$Lambda(?:\\$\\d+)?(?:/(?:0x)?\\p{XDigit}+)?$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HiddenLambdaConditions() {
    }

    /// Rewrites `metadataFile` in place when one of its conditions names a hidden lambda class.
    /// Returns whether the file changed; a missing file is left alone.
    public static boolean rewriteFile(Path metadataFile) throws IOException {
        if (!Files.isRegularFile(metadataFile)) {
            return false;
        }
        JsonNode metadata = OBJECT_MAPPER.readTree(metadataFile.toFile());
        if (!rewrite(metadata)) {
            return false;
        }
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentObjectsWith(indenter);
        prettyPrinter.indentArraysWith(indenter);
        String json = OBJECT_MAPPER.writer(prettyPrinter).writeValueAsString(metadata) + "\n";
        Files.writeString(metadataFile, json, StandardCharsets.UTF_8);
        return true;
    }

    /// Rewrites every hidden-lambda condition under `node` and drops array elements the rewrite
    /// made identical to an earlier one. Returns whether anything changed.
    public static boolean rewrite(JsonNode node) {
        if (node instanceof ArrayNode array) {
            boolean changed = false;
            for (JsonNode element : array) {
                changed |= rewrite(element);
            }
            if (changed) {
                removeDuplicates(array);
            }
            return changed;
        }
        if (!(node instanceof ObjectNode object)) {
            return false;
        }
        boolean changed = false;
        if (object.get("condition") instanceof ObjectNode condition) {
            changed = rewriteCondition(condition);
        }
        for (Map.Entry<String, JsonNode> field : object.properties()) {
            if (!field.getKey().equals("condition")) {
                changed |= rewrite(field.getValue());
            }
        }
        return changed;
    }

    /// Returns the host class of a hidden lambda class name, or `typeName` unchanged.
    public static String hostClass(String typeName) {
        Matcher matcher = HIDDEN_LAMBDA.matcher(typeName);
        return matcher.matches() ? matcher.group(1) : typeName;
    }

    private static boolean rewriteCondition(ObjectNode condition) {
        boolean changed = false;
        for (String key : CONDITION_KEYS) {
            JsonNode value = condition.get(key);
            if (value == null || !value.isTextual()) {
                continue;
            }
            String host = hostClass(value.asText());
            if (!host.equals(value.asText())) {
                condition.put(key, host);
                changed = true;
            }
        }
        return changed;
    }

    private static void removeDuplicates(ArrayNode array) {
        List<JsonNode> distinct = new ArrayList<>();
        for (JsonNode element : array) {
            if (!distinct.contains(element)) {
                distinct.add(element);
            }
        }
        array.removeAll();
        array.addAll(distinct);
    }
}
