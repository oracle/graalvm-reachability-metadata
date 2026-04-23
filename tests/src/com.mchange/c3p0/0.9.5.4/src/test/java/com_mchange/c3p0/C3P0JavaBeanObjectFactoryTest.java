/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.impl.C3P0JavaBeanObjectFactory;
import com.mchange.v2.c3p0.impl.JndiRefDataSourceBase;
import org.junit.jupiter.api.Test;

import javax.naming.Reference;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class C3P0JavaBeanObjectFactoryTest {
    @Test
    void recreatesIdentityTokenizedBeansFromReferences() throws Exception {
        JndiRefDataSourceBase original = new JndiRefDataSourceBase(false);
        original.setCaching(false);
        original.setFactoryClassLocation("factory-location");
        original.setIdentityToken("factory-" + UUID.randomUUID());
        original.setJndiName("jdbc/factory");

        Reference reference = original.getReference();
        Object recreated = new C3P0JavaBeanObjectFactory().getObjectInstance(reference, null, null, new Properties());

        assertThat(recreated).isInstanceOf(JndiRefDataSourceBase.class);
        assertThat(((JndiRefDataSourceBase) recreated).getJndiName()).isEqualTo("jdbc/factory");
    }
}
