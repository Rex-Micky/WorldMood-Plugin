# Version test harness

WorldMood ships two jars spanning Minecraft 1.16.5 – 1.21.x. That range crosses two waves of
Bukkit API renames, so "it compiles" proves very little — the failures that matter
(`NoSuchFieldError`, `NoSuchMethodError`) only appear at runtime, on a real server, on the
version you didn't try.

These scripts boot real Paper servers and drive the plugin through them over **RCON**, so the
whole matrix can be checked without a Minecraft client or a human.

## `version_test.py`

```bash
python version_test.py <paper-jar> <plugin-jar> [java-exe]
```

Creates a throwaway server directory, writes `eula.txt` and a `server.properties` with RCON
enabled, drops the plugin in `plugins/`, boots the server, waits for `RCON running`, then runs
every mood through `start` → `stop` and shuts down. It reports how many moods started versus
stopped, anything the Compat layer disabled on that version, and any exception in the log.
Exits non-zero on failure.

Run each server on a Java version it actually supports:

| Minecraft | Java |
| :---- | :---- |
| 1.16.5 | 8 |
| 1.17 – 1.19 | 17 |
| 1.20+ | 17 or 21 |

## `console_volume_test.py`

```bash
python console_volume_test.py <paper-jar> <java-exe>
```

A **control**. Boots a server with an empty `plugins/` folder, spams `say`, and shuts down.

It exists because Paper 1.17.1 and 1.19.4 segfault at shutdown in their own bundled `jansi`
native console library on Windows (`jansi-2.4.0-*.dll+0x29bf`), and that looked like a plugin
bug until this script reproduced it with no plugin installed at all. Reach for it before
attributing any shutdown crash to plugin code.

The lesson it encodes: the plausible explanation is not automatically the true one. The first
theory here — "Paper 1.19.4 only supports Java 17–19 and we're running 21" — was wrong. It
crashed on Java 17 too. Only the no-plugin control found the real cause.
