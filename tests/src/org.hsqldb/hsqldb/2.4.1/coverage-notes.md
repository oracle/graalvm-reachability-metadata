# Coverage notes

- `ConnectionDialog` and `ConnectionDialogSwing` are package-private. Their connection helpers have no accessible public entry point outside the graphical database-manager flows. `ConnectionDialogCommon` loads and stores recent settings only from these dialogs. Exercising those paths would require an interactive AWT/Swing environment; the command-line help paths are tested without opening windows.
- `JDBCParameterMetaData.toStringImpl` invokes every public one-argument method with an `Integer`, including `unwrap(Class)` and `isWrapperFor(Class)`. Its reflection path cannot produce a successful description for a nonempty parameter list. The tests cover the supported typed metadata API rather than asserting the caught implementation error.
- The `catalog` resource database contains two cached product rows and one binary large object. It was created through JDBC and shut down cleanly before being placed on the classpath. `RAFileInJarTest` and `LobStoreInJarTest` query it through resource-database JDBC URLs.
- The reporter supplied only Maven coordinates, with no explicit missing metadata entry to reproduce.
