# Notes — scheduler/US-000025

* The shutdown is `ApplicationShutdown` (package `cli`): ordered cleanups, idempotent, then exit with code 0 through an injected `IntConsumer`, so the test records the code instead of ending the JVM. `CLILauncher.shutdown()` is the entry point for the tray (US-000054); from another thread it hops to the FX thread because the cleanup hides windows.
* "Stays alive" scenarios rely on `Platform.setImplicitExit(false)`, already in `CLILauncher.init()` and covered by `CLILauncherTest`; no new code was needed for them.
* The JVM shutdown hook (Ctrl+C) is unchanged; it runs after `System.exit` too and is harmless then (closing is idempotent).
