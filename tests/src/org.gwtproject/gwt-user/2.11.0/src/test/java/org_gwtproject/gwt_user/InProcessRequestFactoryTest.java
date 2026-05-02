/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.web.bindery.requestfactory.shared.EntityProxy;
import com.google.web.bindery.requestfactory.shared.RequestFactory;
import com.google.web.bindery.requestfactory.vm.RequestFactorySource;
import com.google.web.bindery.requestfactory.vm.impl.Deobfuscator;
import com.google.web.bindery.requestfactory.vm.impl.OperationKey;

import org.junit.jupiter.api.Test;

public class InProcessRequestFactoryTest {
    @Test
    void resolvesProxyClassFromGeneratedTypeTokenMapping() {
        TestRequestFactory factory = RequestFactorySource.create(TestRequestFactory.class);
        String historyToken = factory.getHistoryToken(TestProxy.class);

        Class<? extends EntityProxy> proxyClass = factory.getProxyClass(historyToken);

        assertThat(proxyClass).isEqualTo(TestProxy.class);
    }

    public interface TestRequestFactory extends RequestFactory {
    }

    public interface TestProxy extends EntityProxy {
    }

    public static final class TestRequestFactoryDeobfuscatorBuilder extends Deobfuscator.Builder {
        public TestRequestFactoryDeobfuscatorBuilder() {
            withRawTypeToken(
                    OperationKey.hash(TestProxy.class.getName()),
                    TestProxy.class.getName());
        }
    }
}
