/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.codegen.intfc.DelegatorGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

public class DelegatorGeneratorTest {
    @Test
    void writeDelegatorIncludesInheritedInterfaceMethods() throws IOException {
        DelegatorGenerator generator = new DelegatorGenerator();
        StringWriter writer = new StringWriter();

        generator.writeDelegator(ChildContract.class, "com_mchange.mchange_commons_java.GeneratedDelegator", writer);

        String generatedSource = writer.toString();

        assertThat(generatedSource).contains("package com_mchange.mchange_commons_java;");
        assertThat(generatedSource)
                .contains("abstract class GeneratedDelegator implements DelegatorGeneratorTest.ChildContract");
        assertThat(generatedSource).contains("parentMessage()");
        assertThat(generatedSource).contains("return inner.parentMessage();");
        assertThat(generatedSource).contains("childValue()");
        assertThat(generatedSource).contains("return inner.childValue();");
    }

    public interface ParentContract {
        String parentMessage();
    }

    public interface ChildContract extends ParentContract {
        int childValue();
    }
}
