/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo.visibility;

public final class HiddenInterfaceProvider {
    private HiddenInterfaceProvider() { }

    public static Class<?> type() {
        return HiddenService.class;
    }

    private interface HiddenService {
        String call(String value);
    }
}
