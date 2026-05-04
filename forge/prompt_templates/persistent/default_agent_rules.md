Workflow rules:
- Modify only files the workflow made editable.
- Use only the target library's public API unless a prompt explicitly says otherwise.
- Keep generated tests meaningful and avoid replacing real coverage with trivial assertions.
- Keep tests compatible with Native Image by default.
- Never generate, write, or modify reachability metadata or Native Image config entries. Do not create or edit `reachability-metadata.json`, `reflect-config.json`, `resource-config.json`, `proxy-config.json`, `serialization-config.json`, `jni-config.json`, `predefined-classes-config.json`, or any other file under `src/test/resources/META-INF/native-image`; Forge handles metadata generation and merging externally.
- Do not skip Native Image execution with runtime guards or native-image-specific disables.
- Do not compile, run, or verify tests yourself; the workflow runs validation externally.
- Keep edits focused on the active library and requested workflow task.
