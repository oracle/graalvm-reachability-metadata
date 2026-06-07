# Post-generation intervention report

Library: com.baomidou:mybatis-plus-boot-starter:3.5.3.1
Stage: metadata_fix_failed

## Summary

The native test run failed after metadata fixing. The Gradle excerpt shows Spring Boot
failing to read `com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration`
metadata because the native image did not contain the corresponding `.class` resource.
The Codex log shows that Codex partially addressed that class-resource problem and other
Spring/MyBatis metadata gaps, but the run still ended with five failing tests.

## Root cause by failure

- `autoConfigurationCreatesMapperBackedBySqlSessionFactoryAndTemplate()`,
  `propertiesCustomizerBeanContributesSettingsBeforeSqlSessionTemplateCreation()`, and
  `autoConfigurationRegistersMapperScannerForAutoConfigurationPackage()` were removed.
  After the metadata-fix attempt progressed past the initial missing auto-configuration
  resource, these tests still failed because Spring's ASM reader from this Spring Boot
  generation cannot parse the generated Java 21 test configuration classes
  (`Unsupported class file major version 65`). That is a generated-test/platform
  incompatibility, not missing reachability metadata.
- `springBootVfsListsClassesFromPackageUrl()` was removed. It depends on
  `ClassLoader.getResource("com_baomidou/mybatis_plus_boot_starter")` returning a package
  directory resource from a native image. That package-directory lookup is not a reliable
  library behavior to validate in native image and is a generated-test issue.
- The failure shown in the Gradle excerpt is metadata-related: Spring Boot's
  `AutoConfigurationSorter` needs the class resource
  `com/baomidou/mybatisplus/autoconfigure/MybatisPlusAutoConfiguration.class` available
  so it can read annotation metadata in the native image.
- The remaining `propertiesResolveMapperLocationsAndRetainFluentCustomizations()` failure
  is also metadata-related. MyBatis tries to load
  `org.apache.ibatis.javassist.util.proxy.ProxyFactory` by name while constructing
  `MybatisConfiguration`, and the class is absent from the native image. Codex did not
  finish the additional MyBatis/Javassist reachability metadata needed for that path.

## Preserved generated support

The remaining tests still exercise useful native-image support for the starter:

- `springFactoriesAdvertiseStarterAutoConfigurationAndEnvironmentProcessor()` preserves
  coverage that the starter's Spring factories advertise both MyBatis-Plus auto-
  configuration and `SafetyEncryptProcessor`.
- `safetyEncryptProcessorDecryptsOriginTrackedProperties()` preserves concrete coverage
  for the environment post-processor and AES-backed `mpw:` property decryption path.
- `propertiesResolveMapperLocationsAndRetainFluentCustomizations()` is intentionally kept
  because its current failure identifies missing MyBatis/Javassist metadata rather than a
  bad generated assertion.

No metadata files were modified during this intervention.
