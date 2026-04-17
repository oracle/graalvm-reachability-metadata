/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.seata.common.exception.FrameworkException;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.model.BranchStatus;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.core.model.Resource;
import org.apache.seata.core.model.ResourceManager;
import org.apache.seata.rm.DefaultResourceManager;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.apache.seata.spring.tcc.TccAnnotationProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TccAnnotationProcessorTest {
    private ResourceManager previousTccResourceManager;

    @AfterEach
    void tearDown() {
        if (previousTccResourceManager != null) {
            DefaultResourceManager.mockResourceManager(BranchType.TCC, previousTccResourceManager);
        }
    }

    @Test
    void addTccAdviseReplacesTheReferencedServiceWithAProxy() throws Exception {
        TccAnnotationProcessor processor = new TccAnnotationProcessor();
        SelfReferencingTccClient bean = new SelfReferencingTccClient();
        Field referenceField = SelfReferencingTccClient.class.getField("reference");

        previousTccResourceManager = currentTccResourceManager();
        DefaultResourceManager.mockResourceManager(BranchType.TCC, new CapturingResourceManager());

        processor.addTccAdvise(bean, "selfReferencingTccClient", referenceField, TccAction.class);

        assertThat(bean.reference).isInstanceOf(TccAction.class);
        assertThat(bean.reference).isNotSameAs(bean);
        assertThat(bean.reference.getClass()).isNotEqualTo(SelfReferencingTccClient.class);
    }

    private ResourceManager currentTccResourceManager() {
        try {
            return DefaultResourceManager.get().getResourceManager(BranchType.TCC);
        } catch (FrameworkException exception) {
            return null;
        }
    }

    public interface TccAction {
        @TwoPhaseBusinessAction(name = "annotation-processor-action")
        boolean prepare(BusinessActionContext actionContext);

        boolean commit(BusinessActionContext actionContext);

        boolean rollback(BusinessActionContext actionContext);
    }

    public static class SelfReferencingTccClient implements TccAction {
        public TccAction reference = this;

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

    public static final class CapturingResourceManager implements ResourceManager {
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
