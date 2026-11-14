# Notes — settings/US-000044

* `docs/settings.md`: the keys (all seven stored ones, with meaning and default), a section per platform (where, how to read, how to reset), `--print-config`, and the files that are not in the preferences (`signin.json`, `calendar.txt`, `dismissed.json`). Linked from the README.
* **Checked**: the Linux section, by writing the node with the Java Preferences API on this Linux workspace (file path, `cat` of the XML, reset with `rm -r` giving the defaults again).
* **Not checked** (no Mac or Windows here): the macOS `defaults read`/`defaults delete` on the node (the page says the exact output and the targeted delete are to be confirmed), and the Windows `reg query`/`reg delete` and the `/` Java writes before capital letters in value names (known Java behaviour). Both sections say so on the page; the scenario "The commands were checked" is therefore met for Linux only. Once someone has run them, remove the italic notes.
* The page tells to quit the application first: a running application keeps what it read and writes it back on save.
