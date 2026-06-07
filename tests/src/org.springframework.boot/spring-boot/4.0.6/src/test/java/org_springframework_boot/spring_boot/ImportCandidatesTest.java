/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.annotation.ImportCandidates;

import static org.assertj.core.api.Assertions.assertThat;

public class ImportCandidatesTest {

    @Test
    void loadReadsCandidatesFromImportsResourcesOnClasspath() {
        ClassLoader classLoader = getClass().getClassLoader();

        ImportCandidates candidates = ImportCandidates.load(ImportCandidateMarker.class, classLoader);

        assertThat(candidates.getCandidates()).containsExactly(String.class.getName(), ArrayList.class.getName());
    }

}

@interface ImportCandidateMarker {

}
