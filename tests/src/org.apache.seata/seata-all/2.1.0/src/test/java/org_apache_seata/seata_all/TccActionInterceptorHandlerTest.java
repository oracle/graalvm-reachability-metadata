/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.lang.reflect.Method;
import java.util.Set;

import org.apache.seata.common.Constants;
import org.apache.seata.common.executor.Callback;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.integration.tx.api.interceptor.ActionInterceptorHandler;
import org.apache.seata.integration.tx.api.interceptor.DefaultInvocationWrapper;
import org.apache.seata.integration.tx.api.interceptor.TwoPhaseBusinessActionParam;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.apache.seata.rm.tcc.interceptor.TccActionInterceptorHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TccActionInterceptorHandlerTest {
    @AfterEach
    void clearRootContext() {
        RootContext.unbindBranchType();
        RootContext.unbind();
    }

    @Test
    void invokeLoadsTheTccAnnotationFromAnImplementedInterfaceMethod() throws Throwable {
        InterfaceAnnotatedTccActionService target = new InterfaceAnnotatedTccActionService();
        CapturingActionInterceptorHandler actionInterceptorHandler = new CapturingActionInterceptorHandler();
        TccActionInterceptorHandler handler = new CapturingTccActionInterceptorHandler(
                target,
                Set.of("prepare"),
                actionInterceptorHandler
        );
        Method method = InterfaceAnnotatedTccActionService.class.getMethod("prepare", String.class);
        DefaultInvocationWrapper invocation = new DefaultInvocationWrapper(
                new Object(),
                target,
                method,
                new Object[]{"order-42"}
        );

        RootContext.bind("xid-123");

        assertThat(handler.invoke(invocation)).isEqualTo("prepared-order-42");
        assertThat(target.lastOrderId).isEqualTo("order-42");
        assertThat(actionInterceptorHandler.invocationCount).isEqualTo(1);
        assertThat(actionInterceptorHandler.branchTypeDuringProceed).isEqualTo(BranchType.TCC);
        assertThat(actionInterceptorHandler.lastMethod).isSameAs(method);
        assertThat(actionInterceptorHandler.lastArguments).containsExactly("order-42");
        assertThat(actionInterceptorHandler.lastXid).isEqualTo("xid-123");
        assertThat(actionInterceptorHandler.lastBusinessActionParam.getActionName()).isEqualTo("interface-action");
        assertThat(actionInterceptorHandler.lastBusinessActionParam.getBranchType()).isEqualTo(BranchType.TCC);
        assertThat(actionInterceptorHandler.lastBusinessActionParam.getUseCommonFence()).isFalse();
        assertThat(actionInterceptorHandler.lastBusinessActionParam.getBusinessActionContext())
                .containsEntry(Constants.ACTION_NAME, "interface-action")
                .containsEntry(Constants.COMMIT_METHOD, "commit")
                .containsEntry(Constants.ROLLBACK_METHOD, "rollback")
                .containsEntry(Constants.USE_COMMON_FENCE, false);
    }

    public interface InterfaceAnnotatedTccAction {
        @TwoPhaseBusinessAction(name = "interface-action")
        String prepare(String orderId);

        boolean commit(Object actionContext);

        boolean rollback(Object actionContext);
    }

    public static final class InterfaceAnnotatedTccActionService implements InterfaceAnnotatedTccAction {
        private String lastOrderId;

        @Override
        public String prepare(String orderId) {
            lastOrderId = orderId;
            return "prepared-" + orderId;
        }

        @Override
        public boolean commit(Object actionContext) {
            return true;
        }

        @Override
        public boolean rollback(Object actionContext) {
            return true;
        }
    }

    public static final class CapturingTccActionInterceptorHandler extends TccActionInterceptorHandler {
        public CapturingTccActionInterceptorHandler(Object targetBean, Set<String> methodsToProxy,
                                                    ActionInterceptorHandler actionInterceptorHandler) {
            super(targetBean, methodsToProxy);
            this.actionInterceptorHandler = actionInterceptorHandler;
        }
    }

    public static final class CapturingActionInterceptorHandler extends ActionInterceptorHandler {
        private int invocationCount;
        private BranchType branchTypeDuringProceed;
        private Method lastMethod;
        private Object[] lastArguments;
        private String lastXid;
        private TwoPhaseBusinessActionParam lastBusinessActionParam;

        @Override
        public Object proceed(Method method, Object[] arguments, String xid, TwoPhaseBusinessActionParam businessActionParam,
                              Callback<Object> targetCallback) throws Throwable {
            invocationCount++;
            branchTypeDuringProceed = RootContext.getBranchType();
            lastMethod = method;
            lastArguments = arguments.clone();
            lastXid = xid;
            lastBusinessActionParam = businessActionParam;
            return targetCallback.execute();
        }
    }
}
