/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.apache.seata.common.loader.EnhancedServiceLoader;
import org.apache.seata.config.ConfigurationFactory;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.model.BranchStatus;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.rm.tcc.TCCResource;
import org.apache.seata.rm.tcc.TCCResourceManager;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TCCResourceManagerTest {
    private static final String CONFIG_TYPE_PROPERTY = "config.type";
    private static final String CONFIG_FILE_NAME_PROPERTY = "config.file.name";
    private static final String ACTION_NAME = "sampleTccAction";
    private static final String XID = "127.0.0.1:8091:1";
    private static final long BRANCH_ID = 11L;

    private TCCResourceManager resourceManager;
    private SampleTccAction action;

    @BeforeEach
    void setUpTccResource() throws NoSuchMethodException {
        resetSeataConfiguration();
        resourceManager = new TCCResourceManager();
        action = new SampleTccAction();

        TCCResource resource = new TCCResource();
        resource.setActionName(ACTION_NAME);
        resource.setTargetBean(action);
        resource.setCommitMethodName("commit");
        resource.setCommitMethod(method("commit", BusinessActionContext.class, String.class));
        resource.setCommitArgsClasses(new Class<?>[] {BusinessActionContext.class, String.class});
        resource.setPhaseTwoCommitKeys(new String[] {null, "orderId"});
        resource.setRollbackMethodName("rollback");
        resource.setRollbackMethod(method("rollback", BusinessActionContext.class, String.class));
        resource.setRollbackArgsClasses(new Class<?>[] {BusinessActionContext.class, String.class});
        resource.setPhaseTwoRollbackKeys(new String[] {null, "reason"});

        resourceManager.getManagedResources().put(ACTION_NAME, resource);
    }

    @Test
    void branchCommitInvokesTccCommitMethodWithBusinessActionContext() throws TransactionException {
        BranchStatus status = resourceManager.branchCommit(
                BranchType.TCC,
                XID,
                BRANCH_ID,
                ACTION_NAME,
                applicationData("orderId", "order-1"));

        assertThat(status).isEqualTo(BranchStatus.PhaseTwo_Committed);
        assertThat(action.committed).isTrue();
        assertThat(action.commitOrderId).isEqualTo("order-1");
        assertThat(action.commitContext.getXid()).isEqualTo(XID);
        assertThat(action.commitContext.getBranchId()).isEqualTo(BRANCH_ID);
        assertThat(action.commitContext.getActionName()).isEqualTo(ACTION_NAME);
    }

    @Test
    void branchRollbackInvokesTccRollbackMethodWithBusinessActionContext() throws TransactionException {
        BranchStatus status = resourceManager.branchRollback(
                BranchType.TCC,
                XID,
                BRANCH_ID,
                ACTION_NAME,
                applicationData("reason", "customer-request"));

        assertThat(status).isEqualTo(BranchStatus.PhaseTwo_Rollbacked);
        assertThat(action.rolledBack).isTrue();
        assertThat(action.rollbackReason).isEqualTo("customer-request");
        assertThat(action.rollbackContext.getXid()).isEqualTo(XID);
        assertThat(action.rollbackContext.getBranchId()).isEqualTo(BRANCH_ID);
        assertThat(action.rollbackContext.getActionName()).isEqualTo(ACTION_NAME);
    }

    private static void resetSeataConfiguration() {
        System.setProperty(CONFIG_TYPE_PROPERTY, "file");
        System.setProperty(CONFIG_FILE_NAME_PROPERTY, "file.conf");
        EnhancedServiceLoader.unload(io.seata.config.ConfigurationProvider.class);
        ConfigurationFactory.reload();
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return SampleTccAction.class.getMethod(name, parameterTypes);
    }

    private static String applicationData(String key, String value) {
        return "{\"actionContext\":{\"" + key + "\":\"" + value + "\"}}";
    }

    public static class SampleTccAction {
        private boolean committed;
        private boolean rolledBack;
        private BusinessActionContext commitContext;
        private BusinessActionContext rollbackContext;
        private String commitOrderId;
        private String rollbackReason;

        public boolean commit(BusinessActionContext context, String orderId) {
            committed = true;
            commitContext = context;
            commitOrderId = orderId;
            return true;
        }

        public boolean rollback(BusinessActionContext context, String reason) {
            rolledBack = true;
            rollbackContext = context;
            rollbackReason = reason;
            return true;
        }
    }
}
