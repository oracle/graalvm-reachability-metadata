/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package sun.misc;

public interface JavaLangAccess {
    StackTraceElement getStackTraceElement(Throwable throwable, int index);

    int getStackTraceDepth(Throwable throwable);
}
