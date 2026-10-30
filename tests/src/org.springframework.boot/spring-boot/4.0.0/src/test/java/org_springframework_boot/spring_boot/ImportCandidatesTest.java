/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.ImportCandidates;

import static org.assertj.core.api.Assertions.assertThat;

public class ImportCandidatesTest {
    @Test
    void readsOrderedCandidatesAndStripsCommentsAndBlankLines() {
        ImportCandidates imports = ImportCandidates.load(Feature.class, getClass().getClassLoader());

        assertThat(imports.getCandidates()).containsExactly(Primary.class.getName(), Secondary.class.getName());
        assertThat(imports).containsExactly(Primary.class.getName(), Secondary.class.getName());
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Feature {
    }

    @Feature
    public static class Primary {
    }

    @Feature
    public static class Secondary {
    }
}
