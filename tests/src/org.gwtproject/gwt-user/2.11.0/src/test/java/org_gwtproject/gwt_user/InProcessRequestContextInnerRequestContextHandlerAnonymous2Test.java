/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.web.bindery.requestfactory.shared.JsonRpcService;
import com.google.web.bindery.requestfactory.shared.JsonRpcWireName;
import com.google.web.bindery.requestfactory.shared.Request;
import com.google.web.bindery.requestfactory.shared.RequestContext;
import com.google.web.bindery.requestfactory.shared.RequestFactory;
import com.google.web.bindery.requestfactory.vm.RequestFactorySource;
import com.google.web.bindery.requestfactory.vm.impl.Deobfuscator;

import org.junit.jupiter.api.Test;

public class InProcessRequestContextInnerRequestContextHandlerAnonymous2Test {
    @Test
    void delegatesRequestInterfaceMethodToJsonRpcRequest() {
        JsonRpcFactory factory = RequestFactorySource.create(JsonRpcFactory.class);
        JsonRpcContext context = factory.context();

        JsonRpcStringRequest request = context.fetch();
        RequestContext requestContext = request.getRequestContext();

        assertThat(requestContext).isNotNull();
        assertThat(requestContext.isChanged()).isFalse();
    }

    public interface JsonRpcFactory extends RequestFactory {
        JsonRpcContext context();
    }

    @JsonRpcService
    public interface JsonRpcContext extends RequestContext {
        JsonRpcStringRequest fetch();
    }

    @JsonRpcWireName(value = "request.fetch", version = "v1")
    public interface JsonRpcStringRequest extends Request<String> {
    }

    public static final class JsonRpcFactoryDeobfuscatorBuilder extends Deobfuscator.Builder {
        public JsonRpcFactoryDeobfuscatorBuilder() {
        }
    }
}
