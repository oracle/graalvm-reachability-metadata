/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13;

import org.apache.pekko.protobufv3.internal.ExtensionRegistry;
import org.apache.pekko.protobufv3.internal.GeneratedFile;

public final class GeneratedFileProbe extends GeneratedFile {
    private GeneratedFileProbe() {
    }

    public static void addExtensionByName(
            ExtensionRegistry registry,
            String className,
            String fieldName) {
        addOptionalExtension(registry, className, fieldName);
    }
}
