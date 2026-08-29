/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

/**
 * Classifies metadata entry conditions against artifact package ownership.
 *
 * The checker and metadata authoring tasks share this implementation so an
 * entry cannot be accepted by one and rejected by the other. §FS-metadata
 */
public final class MetadataEntryOwnership {
    private static final Set<String> ILLEGAL_TYPE_VALUES = Set.of("java.lang");
    private static final Set<String> PREDEFINED_ALLOWED_PACKAGES = Set.of("java.lang", "java.util");

    private MetadataEntryOwnership() {
    }

    public static String typeReached(JsonNode entry) {
        JsonNode condition = entry.path("condition");
        if (!condition.isObject() || !condition.hasNonNull("typeReached")) {
            return null;
        }
        return condition.get("typeReached").asText();
    }

    public static boolean isIllegalTypeReached(String typeReached) {
        return typeReached != null && ILLEGAL_TYPE_VALUES.stream().anyMatch(typeReached::startsWith);
    }

    public static Set<String> illegalTypeValues() {
        return ILLEGAL_TYPE_VALUES;
    }

    public static boolean isAllowed(JsonNode entry, List<String> allowedPackages) {
        String typeReached = typeReached(entry);
        return typeReached == null
                || isAllowedPredefinedEntry(typeReached, entry)
                || matchesAllowedPackage(typeReached, allowedPackages);
    }

    public static boolean isForeign(JsonNode entry, List<String> allowedPackages) {
        String typeReached = typeReached(entry);
        return typeReached != null
                && !isIllegalTypeReached(typeReached)
                && !isAllowedPredefinedEntry(typeReached, entry)
                && !matchesAllowedPackage(typeReached, allowedPackages);
    }

    public static boolean matchesAllowedPackage(String typeReached, List<String> allowedPackages) {
        return allowedPackages.stream().anyMatch(typeReached::contains);
    }

    public static boolean isAllowedPredefinedEntry(String typeReached, JsonNode entry) {
        String entryName = entryName(entry);
        return entryName != null
                && PREDEFINED_ALLOWED_PACKAGES.stream().anyMatch(typeReached::contains)
                && PREDEFINED_ALLOWED_PACKAGES.stream().anyMatch(entryName::contains);
    }

    public static String describeEntry(JsonNode entry) {
        if (entry == null || entry.isMissingNode() || entry.isNull()) {
            return "<missing>";
        }
        if (entry.hasNonNull("name")) {
            return entry.get("name").asText();
        }
        if (entry.hasNonNull("glob")) {
            return entry.get("glob").asText();
        }
        if (entry.hasNonNull("bundle")) {
            return entry.get("bundle").asText();
        }
        if (entry.hasNonNull("class")) {
            return entry.get("class").asText();
        }
        if (entry.hasNonNull("method")) {
            return entry.get("method").asText();
        }
        if (entry.has("type")) {
            JsonNode type = entry.get("type");
            if (type.isTextual()) {
                return type.asText();
            }
            if (type.has("proxy")) {
                return type.get("proxy").toString();
            }
            if (type.has("lambda")) {
                return type.get("lambda").toString();
            }
        }
        return entry.toString();
    }

    private static String entryName(JsonNode entry) {
        if (entry.hasNonNull("name")) {
            return entry.get("name").asText();
        }
        if (entry.has("type") && entry.get("type").isTextual()) {
            return entry.get("type").asText();
        }
        return null;
    }
}
