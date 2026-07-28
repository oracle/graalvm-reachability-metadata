/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.apache.tika.fork.RecursiveMetadataContentHandlerProxyAccess;

public class RecursiveMetadataContentHandlerProxyTest {

    @Test
    public void endDocumentSerializesMetadataToForkProtocol() throws Exception {
        int metadataLength =
                RecursiveMetadataContentHandlerProxyAccess.serializedMainDocumentMetadataLength();

        assertThat(metadataLength).isPositive();
    }
}
