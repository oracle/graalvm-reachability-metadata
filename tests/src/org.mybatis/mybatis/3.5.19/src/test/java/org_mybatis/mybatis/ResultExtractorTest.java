/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.List;

import org.apache.ibatis.executor.ResultExtractor;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ResultExtractorTest {
    @Test
    void extractsReferenceAndPrimitiveArrayResults() {
        Configuration configuration = new Configuration();
        ResultExtractor extractor = new ResultExtractor(configuration, configuration.getObjectFactory());
        assertArrayEquals(new int[]{2, 4}, (int[]) extractor.extractObjectFromList(List.of(2, 4), int[].class));
        assertArrayEquals(new String[]{"Ada", "Grace"},
                (String[]) extractor.extractObjectFromList(List.of("Ada", "Grace"), String[].class));
    }
}
