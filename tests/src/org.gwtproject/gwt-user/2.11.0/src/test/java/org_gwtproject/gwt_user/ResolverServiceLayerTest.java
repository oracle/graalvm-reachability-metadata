/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.web.bindery.requestfactory.server.ServiceLayer;
import com.google.web.bindery.requestfactory.server.ServiceLayerDecorator;
import com.google.web.bindery.requestfactory.shared.EntityProxy;
import com.google.web.bindery.requestfactory.shared.ProxyForName;
import com.google.web.bindery.requestfactory.shared.Request;
import com.google.web.bindery.requestfactory.shared.RequestContext;
import com.google.web.bindery.requestfactory.shared.RequestFactory;
import com.google.web.bindery.requestfactory.shared.ServiceName;
import com.google.web.bindery.requestfactory.vm.impl.Deobfuscator;
import com.google.web.bindery.requestfactory.vm.impl.OperationData;
import com.google.web.bindery.requestfactory.vm.impl.OperationKey;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;

public class ResolverServiceLayerTest {
    private static final String CLIENT_METHOD_DESCRIPTOR =
            "(Ljava/lang/String;Ljava/lang/Integer;)"
                    + "Lcom/google/web/bindery/requestfactory/shared/Request;";
    private static final String DOMAIN_METHOD_DESCRIPTOR =
            "(Ljava/lang/String;Ljava/lang/Integer;)Ljava/lang/String;";

    @Test
    void resolvesRequestFactoryOperationAndDomainMethodFromGeneratedMappings() {
        ServiceLayer serviceLayer = newServiceLayer();
        String operation = operationKey();

        Class<? extends RequestFactory> factory = serviceLayer.resolveRequestFactory(
                TestRequestFactory.class.getName());
        Class<? extends RequestContext> requestContext =
                serviceLayer.resolveRequestContext(operation);
        Method requestContextMethod = serviceLayer.resolveRequestContextMethod(operation);
        Method domainMethod = serviceLayer.resolveDomainMethod(operation);

        assertThat(factory).isEqualTo(TestRequestFactory.class);
        assertThat(requestContext).isEqualTo(TestRequestContext.class);
        assertThat(requestContextMethod.getDeclaringClass()).isEqualTo(TestRequestContext.class);
        assertThat(requestContextMethod.getName()).isEqualTo("save");
        assertThat(requestContextMethod.getParameterTypes())
                .containsExactly(String.class, Integer.class);
        assertThat(domainMethod.getDeclaringClass()).isEqualTo(TestDomainService.class);
        assertThat(domainMethod.getName()).isEqualTo("save");
        assertThat(domainMethod.getParameterTypes()).containsExactly(String.class, Integer.class);
    }

    @Test
    void resolvesNamedProxyAndTypeTokenThroughGeneratedMappings() {
        ServiceLayer serviceLayer = newServiceLayer();
        serviceLayer.resolveRequestFactory(TestRequestFactory.class.getName());

        String typeToken = serviceLayer.resolveTypeToken(TestProxy.class);

        assertThat(serviceLayer.resolveDomainClass(TestProxy.class)).isEqualTo(TestDomain.class);
        assertThat(serviceLayer.resolveClientType(TestDomain.class, EntityProxy.class, true))
                .isEqualTo(TestProxy.class);
        assertThat(serviceLayer.resolveClass(typeToken)).isEqualTo(TestProxy.class);
    }

    private static ServiceLayer newServiceLayer() {
        return ServiceLayer.create(new TestClassLoaderDecorator());
    }

    private static String operationKey() {
        return new OperationKey(
                TestRequestContext.class.getName(),
                "save",
                CLIENT_METHOD_DESCRIPTOR).get();
    }

    private static final class TestClassLoaderDecorator extends ServiceLayerDecorator {
        @Override
        public ClassLoader getDomainClassLoader() {
            return ResolverServiceLayerTest.class.getClassLoader();
        }
    }

    public interface TestRequestFactory extends RequestFactory {
        TestRequestContext testRequest();
    }

    @ServiceName("org_gwtproject.gwt_user.ResolverServiceLayerTest$TestDomainService")
    public interface TestRequestContext extends RequestContext {
        Request<String> save(String value, Integer count);
    }

    @ProxyForName("org_gwtproject.gwt_user.ResolverServiceLayerTest$TestDomain")
    public interface TestProxy extends EntityProxy {
    }

    public static final class TestDomain {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static final class TestDomainService {
        public String save(String value, Integer count) {
            return value + count;
        }
    }

    public static final class TestRequestFactoryDeobfuscatorBuilder extends Deobfuscator.Builder {
        public TestRequestFactoryDeobfuscatorBuilder() {
            withClientToDomainMappings(
                    TestDomain.class.getName(),
                    Collections.singletonList(TestProxy.class.getName()));
            withRawTypeToken(
                    OperationKey.hash(TestProxy.class.getName()),
                    TestProxy.class.getName());
            withOperation(
                    new OperationKey(
                            TestRequestContext.class.getName(),
                            "save",
                            CLIENT_METHOD_DESCRIPTOR),
                    new OperationData.Builder()
                            .withRequestContext(TestRequestContext.class.getName())
                            .withMethodName("save")
                            .withClientMethodDescriptor(CLIENT_METHOD_DESCRIPTOR)
                            .withDomainMethodDescriptor(DOMAIN_METHOD_DESCRIPTOR)
                            .build());
        }
    }
}
