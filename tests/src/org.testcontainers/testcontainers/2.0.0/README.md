# Testcontainers metadata

Run the `main` method and merge it together with the agent-generated `reflect-config.json`. Additionally, this has to
be added:

```json
{
  "condition": {
    "typeReachable": "org.testcontainers.shaded.org.awaitility.core.ConditionFactory"
  },
  "name": "org.testcontainers.shaded.org.hamcrest.TypeSafeMatcher",
  "allDeclaredMethods": true
}
```

This has to be put in the `proxy-config.json`:

```json
[
  {
    "condition": {
      "typeReachable": "org.testcontainers.dockerclient.RootlessDockerClientProviderStrategy"
    },
    "interfaces": [
      "org.testcontainers.dockerclient.RootlessDockerClientProviderStrategy$LibC"
    ]
  }
]

```
