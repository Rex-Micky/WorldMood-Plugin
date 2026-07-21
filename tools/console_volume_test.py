"""Does Paper 1.19.4 crash on shutdown purely from console-output volume?

No plugin installed. Boots the server, spams `say` to produce a comparable amount of
console output to a WorldMood mood sweep, stops it, and reports whether the JVM crashed.
"""
import pathlib
import shutil
import socket
import struct
import subprocess
import sys
import time

SCRATCH = pathlib.Path(__file__).resolve().parent
PORT = 25588
PASSWORD = "worldmoodtest"


def rcon(commands):
    s = socket.create_connection(("127.0.0.1", PORT), timeout=25)
    s.settimeout(25)

    def pack(i, t, b):
        p = struct.pack("<ii", i, t) + b.encode() + b"\x00\x00"
        return struct.pack("<i", len(p)) + p

    def rd():
        r = b""
        while len(r) < 4:
            r += s.recv(4 - len(r))
        (n,) = struct.unpack("<i", r)
        b = b""
        while len(b) < n:
            b += s.recv(n - len(b))
        return b[8:-2].decode("utf8", "replace")

    s.sendall(pack(1, 3, PASSWORD))
    rd()
    for i, c in enumerate(commands, start=2):
        s.sendall(pack(i, 2, c))
        rd()
        time.sleep(0.05)
    s.close()


def main():
    paper = pathlib.Path(sys.argv[1]).resolve()
    java = sys.argv[2]

    run = SCRATCH / "volume194"
    shutil.rmtree(run, ignore_errors=True)
    (run / "plugins").mkdir(parents=True)
    (run / "eula.txt").write_text("eula=true\n")
    (run / "server.properties").write_text(
        f"enable-rcon=true\nrcon.password={PASSWORD}\nrcon.port={PORT}\n"
        "online-mode=false\nview-distance=4\nsimulation-distance=4\nmax-players=5\n")

    log = run / "server.log"
    with open(log, "wb") as sink:
        proc = subprocess.Popen([java, "-Xmx2G", "-jar", str(paper), "--nogui"],
                                cwd=run, stdout=sink, stderr=subprocess.STDOUT,
                                stdin=subprocess.DEVNULL)
        deadline = time.time() + 300
        while time.time() < deadline and proc.poll() is None:
            if "RCON running" in log.read_text("utf8", "replace"):
                break
            time.sleep(3)
        else:
            proc.kill()
            print("failed to boot")
            return 1

        time.sleep(3)
        # A mood sweep produces roughly 40-60 broadcast/log lines. Match that scale.
        rcon([f"say console volume line {i}" for i in range(60)])
        rcon(["stop"])

        for _ in range(60):
            if proc.poll() is not None:
                break
            time.sleep(1)
        else:
            proc.kill()

    text = log.read_text("utf8", "replace")
    crashed = "EXCEPTION_ACCESS_VIOLATION" in text
    hs_err = list(run.glob("hs_err*"))
    print("########## 1.19.4, NO PLUGIN, HEAVY CONSOLE OUTPUT ##########")
    print(f"say lines emitted : {text.count('console volume line')}")
    print(f"JVM crashed       : {crashed}")
    print(f"hs_err files      : {[p.name for p in hs_err]}")
    if crashed:
        for line in text.splitlines():
            if "Problematic frame" in line or "jansi" in line:
                print("   " + line.strip()[:140])
    print("VERDICT: " + ("crash reproduces WITHOUT the plugin -> not WorldMood"
                         if crashed else
                         "no crash without the plugin -> needs more investigation"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
