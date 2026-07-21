"""Boot an isolated Paper server, drive every mood through it, and report failures.

Usage: python version_test.py <paper-jar> <plugin-jar> [java-exe]

Exits non-zero if the plugin failed to load or anything threw.
"""
import pathlib
import re
import shutil
import socket
import struct
import subprocess
import sys
import time

SCRATCH = pathlib.Path(__file__).resolve().parent
RCON_PORT = 25585
RCON_PASSWORD = "worldmoodtest"

MOODS = ["calm_skies", "infernal_heat", "blood_moon", "prosperous_winds",
         "lucky_day", "shadow_veil", "void_tension"]

# Lines that mean a genuine problem. Paper's own startup noise is excluded.
BAD = re.compile(
    r"(Exception|Caused by:|NoSuchFieldError|NoSuchMethodError|NoClassDefFoundError"
    r"|UnsupportedClassVersionError|Could not load|Error occurred while enabling"
    r"|failed to load|Unhandled exception)", re.I)


def rcon(commands):
    """Run commands over RCON, returning the concatenated responses."""
    sock = socket.create_connection(("127.0.0.1", RCON_PORT), timeout=20)
    sock.settimeout(20)

    def pack(rid, rtype, body):
        payload = struct.pack("<ii", rid, rtype) + body.encode() + b"\x00\x00"
        return struct.pack("<i", len(payload)) + payload

    def read():
        raw = b""
        while len(raw) < 4:
            raw += sock.recv(4 - len(raw))
        (length,) = struct.unpack("<i", raw)
        body = b""
        while len(body) < length:
            body += sock.recv(length - len(body))
        return struct.unpack("<ii", body[:8])[0], body[8:-2].decode("utf8", "replace")

    sock.sendall(pack(1, 3, RCON_PASSWORD))
    if read()[0] == -1:
        raise RuntimeError("RCON auth failed")

    out = []
    for i, cmd in enumerate(commands, start=2):
        sock.sendall(pack(i, 2, cmd))
        out.append(read()[1])
        time.sleep(0.4)
    sock.close()
    return "\n".join(out)


def main():
    paper = pathlib.Path(sys.argv[1]).resolve()
    plugin = pathlib.Path(sys.argv[2]).resolve()
    java = sys.argv[3] if len(sys.argv) > 3 else r"C:\Program Files\Java\jdk-21.0.11\bin\java.exe"

    label = paper.stem
    run = SCRATCH / "testruns" / label
    if run.exists():
        shutil.rmtree(run, ignore_errors=True)
    (run / "plugins").mkdir(parents=True)

    (run / "eula.txt").write_text("eula=true\n")
    (run / "server.properties").write_text(
        f"enable-rcon=true\nrcon.password={RCON_PASSWORD}\nrcon.port={RCON_PORT}\n"
        "online-mode=false\nlevel-type=minecraft\\:normal\nspawn-protection=0\n"
        "max-players=5\nview-distance=4\nsimulation-distance=4\n"
        "pause-when-empty-seconds=-1\nmotd=compat test\n")
    shutil.copy(plugin, run / "plugins" / plugin.name)

    log = run / "server.log"
    print(f"\n{'=' * 68}\n{label}  +  {plugin.name}\n{'=' * 68}")

    with open(log, "wb") as sink:
        proc = subprocess.Popen(
            # Jansi is Paper's native console-colour library and segfaults on shutdown on
            # Windows under some JDK/Paper combinations. Forcing a dumb terminal keeps that
            # out of the results - it has nothing to do with the plugin under test.
            [java, "-Xmx2G", "-Dorg.jline.terminal.dumb=true", "-Djansi.passthrough=true",
             "-jar", str(paper), "--nogui"],
            cwd=run, stdout=sink, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)

        booted = False
        deadline = time.time() + 240
        while time.time() < deadline and proc.poll() is None:
            text = log.read_text("utf8", "replace")
            if "RCON running" in text:
                booted = True
                break
            time.sleep(3)

        if not booted:
            proc.kill()
            text = log.read_text("utf8", "replace")
            print("FAILED TO BOOT")
            print("\n".join(l for l in text.splitlines() if BAD.search(l))[:1500])
            return 1

        time.sleep(3)
        try:
            cmds = ["worldmood list"]
            for mood in MOODS:
                cmds += [f"worldmood start {mood}", "worldmood stop"]
            responses = rcon(cmds)
            rcon(["stop"])
        except Exception as exc:                      # noqa: BLE001
            print(f"RCON ERROR: {exc}")
            proc.kill()
            return 1

        for _ in range(40):
            if proc.poll() is not None:
                break
            time.sleep(1)
        else:
            proc.kill()

    text = log.read_text("utf8", "replace")
    problems = [l for l in text.splitlines() if BAD.search(l)]
    started = text.count("Starting mood:")
    stopped = text.count("Stopping mood:")
    enabled = "WorldMood enabled successfully" in text

    compat = [l.split("]", 1)[-1].strip() for l in text.splitlines() if "[Compat]" in l]

    print(f"plugin enabled : {enabled}")
    print(f"moods started  : {started} / {len(MOODS)}")
    print(f"moods stopped  : {stopped} / {len(MOODS)}")
    for line in compat:
        print(f"compat         : {line}")
    if problems:
        print(f"PROBLEMS ({len(problems)}):")
        for line in problems[:12]:
            print("   " + line.strip()[:170])
    else:
        print("problems       : none")

    ok = enabled and started >= len(MOODS) and stopped >= len(MOODS) and not problems
    print("RESULT         : " + ("PASS" if ok else "FAIL"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
