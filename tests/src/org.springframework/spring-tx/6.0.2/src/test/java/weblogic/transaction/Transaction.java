/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package weblogic.transaction;

import java.io.Serializable;

/**
 * Minimal WebLogic transaction contract used by Spring's WebLogic adapter.
 */
public interface Transaction extends javax.transaction.Transaction {

    void setProperty(String name, Serializable value);
}
