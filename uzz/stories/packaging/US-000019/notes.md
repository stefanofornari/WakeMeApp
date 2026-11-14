# Notes — packaging/US-000019

* Merges packaging.macos/US-000019, packaging.windows/US-000020 and packaging.linux/US-000021 (their jpackage installers, auto-start, native secret stores and sleep/wake are listed as deferred in the story; the full text is in the git history). IDs 20 and 21 are retired.
* Assembly, `wakemeapp.sh`, `dbg`, `scriptutils` (derived from Viber's, without `launch`, `upgrade`, `xstartup`): the shell scripts were run here with a stub `java` (arguments, class path, symlink, path with spaces, `JAVA_OPTS`, `conf/wakemeapp.conf`, `dbg`). The assembly descriptor and the real packaging were not run (no Maven here).
* `wakemeapp.cmd` and `dbg.cmd` are written but **not run anywhere** (no Windows here): the assembly gives them CRLF line endings. To check on Windows: `mvn package`, unpack, run `wakemeapp.cmd`, with a relative `--calendar`, with a path with spaces, and `dbg.cmd wakemeapp.cmd`.
* Status stays WIP until the package has been built and started on at least Linux, and the `.cmd` on Windows.
