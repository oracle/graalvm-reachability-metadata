/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_puppycrawl_tools.checkstyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import com.puppycrawl.tools.checkstyle.PackageObjectFactory;
import com.puppycrawl.tools.checkstyle.TreeWalker;
import org.junit.jupiter.api.Test;

public class PackageObjectFactoryTest {
    @Test
    void createsModuleByFullyQualifiedClassName() throws Exception {
        PackageObjectFactory factory = new PackageObjectFactory(
            Collections.emptySet(), PackageObjectFactory.class.getClassLoader());

        Object module = factory.createModule(TreeWalker.class.getName());

        assertThat(module).isInstanceOf(TreeWalker.class);
    }
}
