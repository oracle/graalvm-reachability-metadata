/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_sink_api;

import org.apache.maven.doxia.sink.Sink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SinkAnonymous1Test {
    @Test
    void roleUsesSinkClassName() {
        assertEquals("org.apache.maven.doxia.sink.Sink", Sink.ROLE);
    }
}
