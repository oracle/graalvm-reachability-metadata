/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.v2.codegen.intfc;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DelegatorGeneratorTest {
    @Test
    void writeDelegatorGeneratesDelegatingMethodsForInheritedInterfaceMethods() throws IOException {
        DelegatorGenerator delegatorGenerator = new DelegatorGenerator();
        StringWriter generatedSourceWriter = new StringWriter();

        delegatorGenerator.writeDelegator(
            GeneratedChildContract.class,
            "com.mchange.v2.codegen.intfc.GeneratedChildContractDelegator",
            generatedSourceWriter
        );

        String generatedSource = generatedSourceWriter.toString();

        assertThat(generatedSource).contains("class GeneratedChildContractDelegator implements GeneratedChildContract");
        assertThat(generatedSource).contains("public String childAction(Writer a) throws IOException");
        assertThat(generatedSource).contains("return inner.childAction(a);");
        assertThat(generatedSource).contains("public CharSequence parentAction()");
        assertThat(generatedSource).contains("return inner.parentAction();");
    }
}

interface GeneratedParentContract {
    CharSequence parentAction();
}

interface GeneratedChildContract extends GeneratedParentContract {
    String childAction(Writer writer) throws IOException;
}
