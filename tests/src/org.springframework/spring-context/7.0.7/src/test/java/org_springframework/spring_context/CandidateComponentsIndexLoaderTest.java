/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.context.index.CandidateComponentsIndex;
import org.springframework.context.index.CandidateComponentsIndexLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CandidateComponentsIndexLoaderTest {
    @Test
    void loadsAndFiltersIndexedComponentsByPackageAndStereotype() {
        CandidateComponentsIndexLoader.clearCache();
        CandidateComponentsIndex index = CandidateComponentsIndexLoader.loadIndex(getClass().getClassLoader());
        assertNotNull(index);
        assertEquals(Set.of("org_springframework.spring_context.components.CatalogService"),
                index.getCandidateTypes("org_springframework.spring_context.components", "org.springframework.stereotype.Service"));
        assertEquals(4, index.getCandidateTypes("org_springframework.spring_context.components",
                "org.springframework.stereotype.Component").size());
        assertEquals(Set.of(), index.getCandidateTypes("unrelated", "org.springframework.stereotype.Component"));
    }
}
