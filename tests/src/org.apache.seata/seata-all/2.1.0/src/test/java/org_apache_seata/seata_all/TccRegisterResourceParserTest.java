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
import org.apache.seata.rm.DefaultResourceManager;
import org.apache.seata.rm.tcc.TCCResource;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.apache.seata.rm.tcc.resource.parser.TccRegisterResourceParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TccRegisterResourceParserTest {
    private CapturingResourceManager resourceManager;

    @BeforeEach
    void installCapturingResourceManager() {
        DefaultResourceManager.get();
        resourceManager = new CapturingResourceManager();
        DefaultResourceManager.mockResourceManager(BranchType.TCC, resourceManager);
    }

    @Test
    void registersAnnotatedActionsFromConcreteClassAndImplementedInterface() {
        SampleTccAction action = new SampleTccAction();
        TccRegisterResourceParser parser = new TccRegisterResourceParser();

        parser.registerResource(action, "sampleTccAction");

        assertThat(resourceManager.getManagedResources()).containsKeys("classAction", "interfaceAction");

        TCCResource classResource = (TCCResource) resourceManager.getManagedResources().get("classAction");
        assertThat(classResource.getTargetBean()).isSameAs(action);
        assertThat(classResource.getPrepareMethod().getName()).isEqualTo("prepareClassAction");
        assertThat(classResource.getCommitMethodName()).isEqualTo("commitClassAction");
        assertThat(classResource.getCommitMethod().getName()).isEqualTo("commitClassAction");
        assertThat(classResource.getRollbackMethodName()).isEqualTo("rollbackClassAction");
        assertThat(classResource.getRollbackMethod().getName()).isEqualTo("rollbackClassAction");
        assertThat(classResource.getPhaseTwoCommitKeys()).containsExactly(null, "orderId");
        assertThat(classResource.getPhaseTwoRollbackKeys()).containsExactly(null, "reason");

        TCCResource interfaceResource = (TCCResource) resourceManager.getManagedResources().get("interfaceAction");
        assertThat(interfaceResource.getTargetBean()).isSameAs(action);
        assertThat(interfaceResource.getCommitMethodName()).isEqualTo("commitInterfaceAction");
        assertThat(interfaceResource.getRollbackMethodName()).isEqualTo("rollbackInterfaceAction");
        assertThat(interfaceResource.getPhaseTwoCommitKeys()).containsExactly((String) null);
        assertThat(interfaceResource.getPhaseTwoRollbackKeys()).containsExactly((String) null);
    }

    public interface TccActionContract {
        @TwoPhaseBusinessAction(
                name = "interfaceAction",
                commitMethod = "commitInterfaceAction",
                rollbackMethod = "rollbackInterfaceAction")
        boolean prepareInterfaceAction(BusinessActionContext context);

        boolean commitInterfaceAction(BusinessActionContext context);

        boolean rollbackInterfaceAction(BusinessActionContext context);
    }

    public static class SampleTccAction implements TccActionContract {
        @TwoPhaseBusinessAction(
                name = "classAction",
                commitMethod = "commitClassAction",
                rollbackMethod = "rollbackClassAction",
                commitArgsClasses = {BusinessActionContext.class, String.class},
                rollbackArgsClasses = {BusinessActionContext.class, String.class})
        public boolean prepareClassAction(BusinessActionContext context, String orderId) {
            return true;
        }

        public boolean commitClassAction(
                BusinessActionContext context,
                @BusinessActionContextParameter("orderId") String orderId) {
            return true;
        }

        public boolean rollbackClassAction(
                BusinessActionContext context,
                @BusinessActionContextParameter(paramName = "reason") String reason) {
            return true;
        }

        @Override
        public boolean prepareInterfaceAction(BusinessActionContext context) {
            return true;
        }

        @Override
        public boolean commitInterfaceAction(BusinessActionContext context) {
            return true;
        }

        @Override
        public boolean rollbackInterfaceAction(BusinessActionContext context) {
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
            return;
        }

        @Override
        public boolean lockQuery(BranchType branchType, String resourceId, String xid, String lockKeys)
                throws TransactionException {
            return false;
        }
    }
}
