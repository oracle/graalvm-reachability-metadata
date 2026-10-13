/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit.moduletwo;

import com_tngtech_archunit.archunit.ImportedAnnotation;

@ImportedAnnotation(
        name = "two",
        exposedPackages = "com_tngtech_archunit.archunit.moduletwo..")
public class ModuleTwoRoot {}
