/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot.packagesource;

import org.springframework.stereotype.Component;

@Component("packageGreeting")
public class PackageGreeting {

    @Override
    public String toString() {
        return "Hello from the package source";
    }
}
