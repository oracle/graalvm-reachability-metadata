/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi;

import org.apache.poi.ss.formula.function.FunctionMetadata;
import org.apache.poi.ss.formula.function.FunctionMetadataRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionMetadataReaderDynamicAccessTest {

    @Test
    void loadsBuiltInFunctionMetadataFromClasspathResource() {
        FunctionMetadata metadata = FunctionMetadataRegistry.getFunctionByName("SUM");

        assertThat(metadata).isNotNull();
        assertThat(metadata.getName()).isEqualTo("SUM");
        assertThat(metadata.getIndex()).isEqualTo(FunctionMetadataRegistry.FUNCTION_INDEX_SUM);
        assertThat(FunctionMetadataRegistry.lookupIndexByName("SUM"))
                .isEqualTo(FunctionMetadataRegistry.FUNCTION_INDEX_SUM);
    }
}
