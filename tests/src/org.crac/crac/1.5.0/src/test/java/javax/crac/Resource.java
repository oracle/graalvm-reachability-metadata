/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax.crac;

public interface Resource {
    void beforeCheckpoint(Context<? extends Resource> context) throws Exception;

    void afterRestore(Context<? extends Resource> context) throws Exception;
}
