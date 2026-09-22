/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.Hashtable;

import javax.naming.StringRefAddr;
import javax.sql.DataSource;

import org.apache.naming.NamingContext;
import org.apache.naming.ResourceLinkRef;
import org.apache.naming.factory.DataSourceLinkFactory;
import org.apache.naming.factory.ResourceLinkFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DataSourceLinkFactoryInnerDataSourceHandlerTest {

    @Test
    void suppliesConfiguredCredentialsWhenProxyOpensConnection() throws Exception {
        DataSourceLinkFactoryTest.RecordingDataSource target =
                new DataSourceLinkFactoryTest.RecordingDataSource();
        NamingContext globalContext = new NamingContext(
                (Hashtable<String, Object>) (Hashtable<?, ?>) new java.util.Properties(), "global-data-source");
        globalContext.bind("sharedDataSource", target);
        DataSourceLinkFactory.setGlobalContext(globalContext);
        ResourceLinkFactory.registerGlobalResourceAccess(globalContext, "localDataSource", "sharedDataSource");
        ResourceLinkRef reference = new ResourceLinkRef(DataSource.class.getName(), "sharedDataSource", null, null);
        reference.add(new StringRefAddr("username", "tomcat"));
        reference.add(new StringRefAddr("password", "secret"));

        try {
            DataSource dataSource = (DataSource) new DataSourceLinkFactory()
                    .getObjectInstance(reference, null, null, null);

            assertThat(dataSource.getConnection()).isNull();
            assertThat(target.credentials()).hasValue("tomcat:secret");
        } finally {
            ResourceLinkFactory.deregisterGlobalResourceAccess(globalContext, "localDataSource");
        }
    }
}
