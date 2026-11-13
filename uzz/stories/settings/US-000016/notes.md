# Notes — settings/US-000016

* Cut agreed with the user: playback is notify/US-000008, choosing it is this story. WAV/AIFF only (no MP3/OGG); the custom path is stored absolute; the bundled "Gentle Bell" is simply "Bell".
* Settings keys: `notify.sound` (chime, pop, ping, bell, none, custom), `notify.soundFile` (only for custom; forgotten for any other choice) and `notify.soundVolume` (0-100, default 80). `SoundSetting` reads and saves them, `SoundChoice` is the value, `SoundFile.validate` checks the file (exists, readable, `AudioSystem` recognises WAV/AIFF).
* The custom file is validated when it is picked, not only on save: a refused file shows the error under the list and the selection returns to the previous entry, so a bad choice can never be saved. Cancelling the picker also reverts.
* Preview goes through `SoundNotifier.preview(choice, volume)`: it stops what is playing, ignores the 1 s sharing of simultaneous notifications and plays the unsaved choice. Volume 0 or "none" plays nothing and does not beep. Save calls `soundSaved` and the launcher re-prepares the sound (preloads the clip).
* A stored custom file that disappeared: the dialog shows a warning label (an icon is not used) and the notification plays the chime with a logged warning.
* The file picker is behind a `Supplier<File>` (`setFilePicker`) so tests do not open a native dialog.
* Not verified here: the FXML/controller/`SettingsDialogTest` (TestFX) and the launcher need `mvn test`. The pure logic (`SoundSetting`, `SoundFile`, `SoundNotifier`, `ConfigReport`) was run. The synthesized tones need listening to on a real machine.
