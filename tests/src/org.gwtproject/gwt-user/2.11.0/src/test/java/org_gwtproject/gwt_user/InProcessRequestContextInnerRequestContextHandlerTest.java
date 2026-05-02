/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.web.bindery.autobean.shared.AutoBean.PropertyName;
import com.google.web.bindery.requestfactory.shared.JsonRpcContent;
import com.google.web.bindery.requestfactory.shared.JsonRpcService;
import com.google.web.bindery.requestfactory.shared.JsonRpcWireName;
import com.google.web.bindery.requestfactory.shared.Request;
import com.google.web.bindery.requestfactory.shared.RequestContext;
import com.google.web.bindery.requestfactory.shared.RequestFactory;
import com.google.web.bindery.requestfactory.vm.RequestFactorySource;
import com.google.web.bindery.requestfactory.vm.impl.Deobfuscator;

import org.junit.jupiter.api.Test;

public class InProcessRequestContextInnerRequestContextHandlerTest {
    @Test
    void delegatesRequestContextMethodsToInProcessContext() {
        TestRequestFactory factory = RequestFactorySource.create(TestRequestFactory.class);
        StandardRequestContext context = factory.standardRequest();

        assertThat(context.isChanged()).isFalse();
    }

    @Test
    void returnsJsonRpcRequestProxyForContextMethod() {
        TestRequestFactory factory = RequestFactorySource.create(TestRequestFactory.class);
        JsonRpcRequestContext context = factory.jsonRpcRequest();

        JsonRpcStringRequest request = context.fetch(
                "visible-name",
                "request-content");
        JsonRpcStringRequest chainedRequest = request.setOptionalValue("configured");

        assertThat(request).isNotNull();
        assertThat(chainedRequest).isSameAs(request);
    }

    public interface TestRequestFactory extends RequestFactory {
        StandardRequestContext standardRequest();

        JsonRpcRequestContext jsonRpcRequest();
    }

    public interface StandardRequestContext extends RequestContext {
    }

    @JsonRpcService
    public interface JsonRpcRequestContext extends RequestContext {
        JsonRpcStringRequest fetch(
                @PropertyName("name") String name,
                @JsonRpcContent String content);
    }

    @JsonRpcWireName(value = "request.fetch", version = "v1")
    public interface JsonRpcStringRequest extends Request<String> {
        JsonRpcStringRequest setOptionalValue(String optionalValue);
    }

    public static final class TestRequestFactoryDeobfuscatorBuilder extends Deobfuscator.Builder {
        public TestRequestFactoryDeobfuscatorBuilder() {
        }
    }
}
