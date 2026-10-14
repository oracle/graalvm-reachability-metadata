/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit.moduleone;

import com_tngtech_archunit.archunit.ImportedAnnotation;
import com_tngtech_archunit.archunit.moduletwo.ModuleTwoRoot;

@ImportedAnnotation(name = "one", allowedDependencies = "two")
public class ModuleOneRoot {
    public ModuleTwoRoot dependency() {
        return new ModuleTwoRoot();
    }
}
