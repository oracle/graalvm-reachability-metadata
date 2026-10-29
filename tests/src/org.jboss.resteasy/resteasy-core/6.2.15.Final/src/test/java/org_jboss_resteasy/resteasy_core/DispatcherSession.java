/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

final class DispatcherSession implements AutoCloseable {
    private final ResteasyProviderFactory previous = ResteasyProviderFactory.peekInstance();
    private final ResteasyContext.CloseableContext context = ResteasyContext.addCloseableContextDataLevel();
    final Dispatcher dispatcher = MockDispatcherFactory.createDispatcher();

    MockHttpResponse get(String path) throws Exception {
        MockHttpResponse response = new MockHttpResponse();
        dispatcher.invoke(MockHttpRequest.get(path), response);
        return response;
    }

    @Override
    public void close() {
        context.close();
        ResteasyProviderFactory.setInstance(previous);
    }
}
