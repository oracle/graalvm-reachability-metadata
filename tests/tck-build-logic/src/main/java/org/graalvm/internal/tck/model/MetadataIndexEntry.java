package org.graalvm.internal.tck.model;

import java.util.List;

/*
 * JSON model for metadata/index.json.
 */
public record MetadataIndexEntry(
        String directory,
        String module,
        List<String> requires
) {
}
