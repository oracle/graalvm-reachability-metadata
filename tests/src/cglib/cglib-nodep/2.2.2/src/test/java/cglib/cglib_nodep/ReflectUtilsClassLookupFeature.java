/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

public final class ReflectUtilsClassLookupFeature implements Feature {
    private static final String[] PRIMITIVE_TYPES = {
            "boolean", "byte", "char", "double", "float", "int", "long", "short"
    };

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // ReflectUtils probes invalid primitive lookup names and expects
        // ClassNotFoundException fallbacks before it reaches primitive handling.
        for (String primitiveType : PRIMITIVE_TYPES) {
            RuntimeReflection.registerClassLookup(primitiveType);
            RuntimeReflection.registerClassLookup("java.lang." + primitiveType);
            RuntimeReflection.registerClassLookup("[L" + primitiveType + ";");
            RuntimeReflection.registerClassLookup("[Ljava.lang." + primitiveType + ";");
        }
    }
}
