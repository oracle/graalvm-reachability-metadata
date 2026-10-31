/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.graalvm.internal.tck.harness.tasks.TestResourceClassifier.referencesAnyPackage;
import static org.graalvm.internal.tck.harness.tasks.TestResourceClassifier.typeNameReferencesAnyPackage;

/// Unit tests of the package matching that decides which type names are test-only.
class TestResourceClassifierTests {
    private static final Set<String> GROOVY_TEST_PACKAGES = Set.of("org_apache_groovy.groovy");

    /// The custom-metaclass lookups Groovy records for the suite's own fixtures (issue 11696).
    @ParameterizedTest
    @ValueSource(strings = {
            "groovy.runtime.metaclass.org_apache_groovy.groovy.InvokerHelperTest$BoundProgramMetaClass",
            "groovy.runtime.metaclass.org_apache_groovy.groovy."
                    + "ObjectGraphBuilderInnerReflectionClassNameResolverTest$RootMetaClass",
            "groovy.runtime.metaclass.org_apache_groovy.groovy.PluginDefaultGroovyMethodsTest$StageMetaClass"
    })
    void typeNameEmbeddingTestPackageIsTestOnly(String typeName) {
        assertThat(typeNameReferencesAnyPackage(typeName, GROOVY_TEST_PACKAGES)).isTrue();
    }

    @Test
    void typeNameInTestPackageIsTestOnly() {
        assertThat(typeNameReferencesAnyPackage("org_apache_groovy.groovy.InvokerHelperTest", GROOVY_TEST_PACKAGES))
                .isTrue();
        assertThat(typeNameReferencesAnyPackage("org_apache_groovy.groovy.sub.Fixture", GROOVY_TEST_PACKAGES))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "groovy.runtime.metaclass.java.lang.StringMetaClass",
            "groovy.runtime.metaclass.org_apache_groovy.groovyx.HelperMetaClass",
            "groovy.runtime.metaclass.xorg_apache_groovy.groovy.HelperMetaClass",
            "groovy.runtime.metaclass.org_apache_groovy.groovy",
            "org.apache.groovy.lang.GroovyObject"
    })
    void libraryTypeNameStaysShipped(String typeName) {
        assertThat(typeNameReferencesAnyPackage(typeName, GROOVY_TEST_PACKAGES)).isFalse();
    }

    /// A single-segment test package such as `h2` sits inside library names by accident, so only
    /// names in it or under it count.
    @Test
    void singleSegmentTestPackageIsNotMatchedEmbedded() {
        Set<String> testPackages = Set.of("h2");

        assertThat(typeNameReferencesAnyPackage("org.h2.Driver", testPackages)).isFalse();
        assertThat(typeNameReferencesAnyPackage("org.h2.mvstore.type.MetaType", testPackages)).isFalse();
        assertThat(typeNameReferencesAnyPackage("h2.H2Test", testPackages)).isTrue();
    }

    /// Resource classification keeps matching packages by whole leading segments only.
    @Test
    void packageReferenceIgnoresEmbeddedTestPackage() {
        assertThat(referencesAnyPackage(
                "groovy.runtime.metaclass.org_apache_groovy.groovy.InvokerHelperTest",
                GROOVY_TEST_PACKAGES
        )).isFalse();
        assertThat(referencesAnyPackage("org_apache_groovy.groovy.InvokerHelperTest", GROOVY_TEST_PACKAGES))
                .isTrue();
    }
}
