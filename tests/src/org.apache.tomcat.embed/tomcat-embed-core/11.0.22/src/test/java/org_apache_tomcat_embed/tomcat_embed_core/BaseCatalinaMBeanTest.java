/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.mbeans.ContainerMBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseCatalinaMBeanTest {

    @Test
    void addsConfiguredValveToManagedContainer() throws Exception {
        StandardContext context = new StandardContext();
        ContainerMBean mBean = new ContainerMBean();
        mBean.setManagedResource(context, "ObjectReference");

        String objectName = mBean.addValve(TestValve.class.getName());

        assertThat(objectName).isNull();
        assertThat(context.getPipeline().getValves()).anyMatch(TestValve.class::isInstance);
    }

    public static final class TestValve implements Valve {

        private Valve next;

        public TestValve() {
        }

        @Override
        public Valve getNext() {
            return next;
        }

        @Override
        public void setNext(Valve valve) {
            next = valve;
        }

        @Override
        public void backgroundProcess() {
        }

        @Override
        public void invoke(Request request, Response response) {
        }

        @Override
        public boolean isAsyncSupported() {
            return true;
        }
    }
}
