/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.seata.common.exception.FrameworkException;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.model.BranchStatus;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.core.model.Resource;
import org.apache.seata.core.model.ResourceManager;
import org.apache.seata.integration.tx.api.interceptor.handler.ProxyInvocationHandler;
import org.apache.seata.rm.DefaultResourceManager;
import org.apache.seata.rm.tcc.TCCResource;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.apache.seata.rm.tcc.interceptor.parser.TccActionInterceptorParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TccActionInterceptorParserTest {
    private ResourceManager previousTccResourceManager;

    @AfterEach
    void tearDown() {
        if (previousTccResourceManager != null) {
            DefaultResourceManager.mockResourceManager(BranchType.TCC, previousTccResourceManager);
        }
    }

    @Test
    void parserInterfaceToProxyDiscoversTwoPhaseMethodsDeclaredOnAnInterface() {
        TccActionInterceptorParser parser = new TccActionInterceptorParser();
        CapturingResourceManager capturingResourceManager = new CapturingResourceManager();
        InterfaceAnnotatedTccService target = new InterfaceAnnotatedTccService();

        previousTccResourceManager = currentTccResourceManager();
        DefaultResourceManager.mockResourceManager(BranchType.TCC, capturingResourceManager);

        ProxyInvocationHandler proxyInvocationHandler = parser.parserInterfaceToProxy(target, "interfaceAnnotatedTccService");

        assertThat(proxyInvocationHandler).isNotNull();
        assertThat(proxyInvocationHandler.getMethodsToProxy()).containsOnly("prepare");
        assertThat(capturingResourceManager.registeredResources)
                .extracting(TCCResource::getResourceId)
                .containsExactly("interface-action");
    }

    @Test
    void parserInterfaceToProxyReturnsNullWhenNoTwoPhaseActionIsPresent() {
        TccActionInterceptorParser parser = new TccActionInterceptorParser();

        ProxyInvocationHandler proxyInvocationHandler = parser.parserInterfaceToProxy(new NonTccService(), "nonTccService");

        assertThat(proxyInvocationHandler).isNull();
    }

    private ResourceManager currentTccResourceManager() {
        try {
            return DefaultResourceManager.get().getResourceManager(BranchType.TCC);
        } catch (FrameworkException exception) {
            return null;
        }
    }

    public interface InterfaceAnnotatedTccAction {
        @TwoPhaseBusinessAction(name = "interface-action")
        boolean prepare(BusinessActionContext actionContext);

        boolean commit(BusinessActionContext actionContext);

        boolean rollback(BusinessActionContext actionContext);
    }

    public static final class InterfaceAnnotatedTccService implements InterfaceAnnotatedTccAction {
        @Override
        public boolean prepare(BusinessActionContext actionContext) {
            return true;
        }

        @Override
        public boolean commit(BusinessActionContext actionContext) {
            return true;
        }

        @Override
        public boolean rollback(BusinessActionContext actionContext) {
            return true;
        }
    }

    public static final class NonTccService {
        public boolean prepare(BusinessActionContext actionContext) {
            return true;
        }
    }

    public static final class CapturingResourceManager implements ResourceManager {
        private final List<TCCResource> registeredResources = new ArrayList<>();
        private final Map<String, Resource> managedResources = new LinkedHashMap<>();

        @Override
        public BranchStatus branchCommit(BranchType branchType, String xid, long branchId, String resourceId,
                                         String applicationData) throws TransactionException {
            return null;
        }

        @Override
        public BranchStatus branchRollback(BranchType branchType, String xid, long branchId, String resourceId,
                                           String applicationData) throws TransactionException {
            return null;
        }

        @Override
        public Long branchRegister(BranchType branchType, String resourceId, String clientId, String xid,
                                   String applicationData, String lockKeys) throws TransactionException {
            return null;
        }

        @Override
        public void branchReport(BranchType branchType, String xid, long branchId, BranchStatus status,
                                 String applicationData) throws TransactionException {
        }

        @Override
        public boolean lockQuery(BranchType branchType, String resourceId, String xid, String lockKeys)
                throws TransactionException {
            return false;
        }

        @Override
        public void registerResource(Resource resource) {
            TCCResource tccResource = (TCCResource) resource;
            registeredResources.add(tccResource);
            managedResources.put(resource.getResourceId(), resource);
        }

        @Override
        public void unregisterResource(Resource resource) {
            managedResources.remove(resource.getResourceId());
        }

        @Override
        public Map<String, Resource> getManagedResources() {
            return managedResources;
        }

        @Override
        public BranchType getBranchType() {
            return BranchType.TCC;
        }

        @Override
        public GlobalStatus getGlobalStatus(BranchType branchType, String xid) {
            return null;
        }
    }
}
