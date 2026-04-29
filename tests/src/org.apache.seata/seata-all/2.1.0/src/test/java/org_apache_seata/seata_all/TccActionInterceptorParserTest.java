/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.model.BranchStatus;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.core.model.Resource;
import org.apache.seata.core.model.ResourceManager;
import org.apache.seata.integration.tx.api.interceptor.handler.ProxyInvocationHandler;
import org.apache.seata.rm.DefaultResourceManager;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.apache.seata.rm.tcc.interceptor.TccActionInterceptorHandler;
import org.apache.seata.rm.tcc.interceptor.parser.TccActionInterceptorParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TccActionInterceptorParserTest {
    @BeforeEach
    void installCapturingResourceManager() {
        DefaultResourceManager.get();
        DefaultResourceManager.mockResourceManager(BranchType.TCC, new CapturingResourceManager());
    }

    @Test
    void createsTccInterceptorForAnnotatedActionInterface() {
        TccActionInterceptorParser parser = new TccActionInterceptorParser();
        SampleTccAction action = new SampleTccAction();

        ProxyInvocationHandler proxyInvocationHandler = parser.parserInterfaceToProxy(action, "sampleTccAction");

        assertThat(proxyInvocationHandler).isInstanceOf(TccActionInterceptorHandler.class);
    }

    @Test
    void ignoresServicesWithoutTccActionAnnotations() {
        TccActionInterceptorParser parser = new TccActionInterceptorParser();

        ProxyInvocationHandler proxyInvocationHandler = parser.parserInterfaceToProxy(new PlainService(), "plainService");

        assertThat(proxyInvocationHandler).isNull();
    }

    public interface TccActionContract {
        @TwoPhaseBusinessAction(
                name = "sampleTccAction",
                commitMethod = "commit",
                rollbackMethod = "rollback")
        boolean prepare(BusinessActionContext context);

        boolean commit(BusinessActionContext context);

        boolean rollback(BusinessActionContext context);
    }

    public static class SampleTccAction implements TccActionContract {
        @Override
        public boolean prepare(BusinessActionContext context) {
            return true;
        }

        @Override
        public boolean commit(BusinessActionContext context) {
            return true;
        }

        @Override
        public boolean rollback(BusinessActionContext context) {
            return true;
        }
    }

    public static class PlainService {
        public boolean doWork() {
            return true;
        }
    }

    private static class CapturingResourceManager implements ResourceManager {
        private final Map<String, Resource> resources = new LinkedHashMap<>();

        @Override
        public void registerResource(Resource resource) {
            resources.put(resource.getResourceId(), resource);
        }

        @Override
        public void unregisterResource(Resource resource) {
            resources.remove(resource.getResourceId());
        }

        @Override
        public Map<String, Resource> getManagedResources() {
            return resources;
        }

        @Override
        public BranchType getBranchType() {
            return BranchType.TCC;
        }

        @Override
        public GlobalStatus getGlobalStatus(BranchType branchType, String xid) {
            return GlobalStatus.UnKnown;
        }

        @Override
        public BranchStatus branchCommit(
                BranchType branchType,
                String xid,
                long branchId,
                String resourceId,
                String applicationData) throws TransactionException {
            return BranchStatus.PhaseTwo_Committed;
        }

        @Override
        public BranchStatus branchRollback(
                BranchType branchType,
                String xid,
                long branchId,
                String resourceId,
                String applicationData) throws TransactionException {
            return BranchStatus.PhaseTwo_Rollbacked;
        }

        @Override
        public Long branchRegister(
                BranchType branchType,
                String resourceId,
                String clientId,
                String xid,
                String applicationData,
                String lockKeys) throws TransactionException {
            return 1L;
        }

        @Override
        public void branchReport(
                BranchType branchType,
                String xid,
                long branchId,
                BranchStatus status,
                String applicationData) throws TransactionException {
        }

        @Override
        public boolean lockQuery(BranchType branchType, String resourceId, String xid, String lockKeys)
                throws TransactionException {
            return true;
        }
    }
}
