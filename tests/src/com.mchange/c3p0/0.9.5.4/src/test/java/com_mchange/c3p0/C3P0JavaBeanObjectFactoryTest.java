/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.impl.C3P0JavaBeanObjectFactory;
import javax.naming.Reference;
import org.junit.jupiter.api.Test;

public class C3P0JavaBeanObjectFactoryTest {
    @Test
    void recreatesIdentityTokenizedDataSourceFromJavaBeanReference() throws Exception {
        ComboPooledDataSource source = configuredDataSource();
        ComboPooledDataSource restored = null;

        try {
            Reference reference = source.getReference();

            Object object = new C3P0JavaBeanObjectFactory().getObjectInstance(reference, null, null, null);
            restored = (ComboPooledDataSource) object;

            assertThat(restored).isNotSameAs(source);
            assertThat(restored.getIdentityToken()).isEqualTo(source.getIdentityToken());
            assertThat(restored.getDataSourceName()).isEqualTo("java-bean-reference-data-source");
            assertThat(restored.getDescription()).isEqualTo("DataSource restored from a JavaBean Reference");
            assertThat(restored.getJdbcUrl()).isEqualTo("jdbc:c3p0-test:java-bean-reference");
            assertThat(restored.getMinPoolSize()).isEqualTo(1);
            assertThat(restored.getMaxPoolSize()).isEqualTo(2);
            assertThat(restored.getNumHelperThreads()).isEqualTo(1);
        } finally {
            if (restored != null) {
                restored.close();
            }
            source.close();
        }
    }

    private static ComboPooledDataSource configuredDataSource() throws Exception {
        ComboPooledDataSource source = new ComboPooledDataSource(false);
        source.setIdentityToken("java-bean-reference-token");
        source.setDataSourceName("java-bean-reference-data-source");
        source.setDescription("DataSource restored from a JavaBean Reference");
        source.setJdbcUrl("jdbc:c3p0-test:java-bean-reference");
        source.setMinPoolSize(1);
        source.setMaxPoolSize(2);
        source.setNumHelperThreads(1);
        return source;
    }
}
