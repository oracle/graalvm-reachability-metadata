/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package weblogic.transaction;

/**
 * Minimal WebLogic UserTransaction contract used by Spring's WebLogic adapter.
 */
public interface UserTransaction extends javax.transaction.UserTransaction {

    void begin(String name);

    void begin(String name, int timeout);
}
