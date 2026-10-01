#!/usr/bin/env python3
"""Run a command while holding a machine-wide lock, so parallel agents never run
two Gradle builds (or emulator sessions) at once. Usage: tools/lockrun.py [--lock name] -- cmd args..."""
import fcntl, os, subprocess, sys, time
args = sys.argv[1:]
name = "gradle"
if args[:1] == ["--lock"]:
    name, args = args[1], args[2:]
if args[:1] == ["--"]:
    args = args[1:]
path = f"/tmp/tff-{name}.lock"
with open(path, "w") as f:
    t0 = time.time()
    fcntl.flock(f, fcntl.LOCK_EX)
    waited = time.time() - t0
    if waited > 2:
        print(f"[lockrun] waited {waited:.0f}s for '{name}' lock", file=sys.stderr)
    sys.exit(subprocess.call(args))
