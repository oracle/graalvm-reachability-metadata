/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package weblogic.transaction;

import javax.transaction.InvalidTransactionException;
import javax.transaction.SystemException;

/**
 * Minimal WebLogic transaction manager contract used by Spring's WebLogic adapter.
 */
public interface ClientTransactionManager extends javax.transaction.TransactionManager {

    void forceResume(javax.transaction.Transaction transaction) throws InvalidTransactionException, SystemException;
}
