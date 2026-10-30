/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_model;

import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.Exclusion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DependencyTest {
    @Test
    void distinguishesDependencyVariantsByManagementKey() {
        Dependency main = Dependency.newBuilder()
                .groupId("com.example").artifactId("client").version("1.0").build();
        Dependency tests = main.with().type("test-jar").classifier("tests")
                .scope("test").optional("true").build();

        assertEquals("com.example:client:jar", main.getManagementKey());
        assertEquals("com.example:client:test-jar:tests", tests.getManagementKey());
        assertEquals("test", tests.getScope());
        assertEquals("1.0", tests.getVersion());
        assertTrue(tests.isOptional());
        assertFalse(main.isOptional());
        assertEquals("jar", main.getType());
        assertEquals("com.example:client:jar", main.withClassifier("").getManagementKey());
    }

    @Test
    void preservesExclusionsWhenChangingDependencyVersion() {
        Exclusion exclusion = Exclusion.newBuilder()
                .groupId("com.example.legacy").artifactId("logging").build();
        List<Exclusion> exclusions = new ArrayList<>(List.of(exclusion));
        Dependency original = Dependency.newBuilder().groupId("com.example").artifactId("client")
                .version("1.0").exclusions(exclusions).build();
        exclusions.clear();
        Dependency updated = original.withVersion("2.0");

        assertEquals("1.0", original.getVersion());
        assertEquals("2.0", updated.getVersion());
        assertEquals(original.getManagementKey(), updated.getManagementKey());
        assertEquals(1, updated.getExclusions().size());
        assertEquals("com.example.legacy", updated.getExclusions().getFirst().getGroupId());
        assertEquals("logging", updated.getExclusions().getFirst().getArtifactId());
        assertThrows(UnsupportedOperationException.class, () -> updated.getExclusions().clear());
    }
}
