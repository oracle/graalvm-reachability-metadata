/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_openfeign_querydsl.querydsl_codegen_utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import com.querydsl.codegen.utils.MemClassLoader;
import org.junit.jupiter.api.Test;

public class MemClassLoaderTest {

    @Test
    void reportsNoResourcesForUnknownName() throws Exception {
        MemClassLoader classLoader = new MemClassLoader(
                getClass().getClassLoader(), Collections.emptyMap());

        assertThat(classLoader.getResources("missing-querydsl-resource").hasMoreElements()).isFalse();
    }
}
