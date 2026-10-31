# Public API coverage limits

- `jakarta.faces.webapp.MessageFactory` is package-private and has no callers in the artifact's sources. The public `FacesServlet` does not use it. It cannot be exercised without prohibited reflection or a test in the library's package.
- The Mojarra fallback bundle branch in the component, convert, and validator message factories is not reached by the tested APIs: their fixed message IDs are present in `jakarta.faces.Messages`. Tests cover standard messages and application-defined overrides without replacing library resources.
