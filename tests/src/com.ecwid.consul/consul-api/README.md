# Consul API

The metadata has been gathered by a combination of running the consul-api library tests with the agent attached and
by tweaking and completing the output files.

## Run tests with agent

Add this to `build.gradle`:

```groovy
apply plugin: 'org.graalvm.buildtools.native'
graalvmNative {
	agent {
		defaultMode = "conditional"
		modes {
			conditional {
				userCodeFilterPath = "user-code-filter.json"
			}
		}
		metadataCopy {
			mergeWithExisting = true
			inputTaskNames.add("test")
		}
	}
}

buildscript {
	dependencies {
		classpath 'org.graalvm.buildtools.native:org.graalvm.buildtools.native.gradle.plugin:0.9.13'
	}
		repositories {
			mavenCentral()
			gradlePluginPortal()
		}
}
```

`user-code-filter.json` contains this:

```json
{
  "rules": [
	{"excludeClasses": "**"},
	{"includeClasses": "com.ecwid.consul.**"}
  ]
}
```
Due to compatibility issues the following test classes have been ignored for the analysis: `AclConsulClientTest.java`, `CatalogConsulClientTest.java`, `ConsulClientTest.java`, `KeyValueConsulClientTest.java`.

Execute the tests with `gradle -Pnative test`.

Entries referring to classes with `Test` in name have been removed. Entries prepared while working with samples have been added.