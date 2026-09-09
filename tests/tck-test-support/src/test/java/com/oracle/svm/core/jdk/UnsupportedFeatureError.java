/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.oracle.svm.core.jdk;

/// Test double for the error Native Image throws for unsupported dynamic operations.
/// The real class exists only inside a native image, and `NativeImageSupport` matches it
/// by fully qualified name, so exercising that check needs a class of the same name here.
public class UnsupportedFeatureError extends Error {

    public UnsupportedFeatureError(String message) {
        super(message);
    }
}
