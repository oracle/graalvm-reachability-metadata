/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * Preserves the expected ClassNotFoundException fallback in ReflectUtils for the synthetic
 * "[Lbyte;" probe that precedes the primitive-array "[B" lookup.
 */
public final class ReflectUtilsNegativeLookupFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeReflection.registerClassLookup("[Lbyte;");
    }
}
