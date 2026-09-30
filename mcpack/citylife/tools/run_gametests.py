#!/usr/bin/env python3
"""
Прогон самотестов мода на настоящем Forge-сервере со всеми модами сборки.

Сервер берётся готовый (каталог с установленным Forge, модами и миром),
в него кладётся свежий jar мода, сервер выполняет /citylife selftest и
останавливается. Лог сервера — в <каталог>/selftest.log. Код выхода 0 —
все тесты прошли.

    python3 citylife/tools/run_gametests.py <каталог сервера> [java]
"""
from __future__ import annotations

import glob
import os
import shutil
import subprocess
import sys
import threading

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, "build", "libs", "citylife-1.20.1-1.0.0.jar")


def main() -> int:
    server = os.path.abspath(sys.argv[1])
    java = sys.argv[2] if len(sys.argv) > 2 else "java"
    shutil.copy(JAR, os.path.join(server, "mods", os.path.basename(JAR)))
    args = glob.glob(os.path.join(server, "libraries/net/minecraftforge/forge/*/unix_args.txt"))[0]
    proc = subprocess.Popen([java, "-Xmx3G", f"@{args}", "nogui"], cwd=server,
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, bufsize=1)
    ready, done = threading.Event(), threading.Event()
    lines: list[str] = []
    log = open(os.path.join(server, "selftest.log"), "w", encoding="utf-8")

    def reader() -> None:
        for line in proc.stdout:
            lines.append(line)
            log.write(line)
            log.flush()
            if "Done (" in line:
                ready.set()
            if "SELFTEST DONE" in line:
                done.set()
        ready.set()
        done.set()

    threading.Thread(target=reader, daemon=True).start()
    if not ready.wait(900):
        proc.kill()
        print("сервер не поднялся")
        return 2
    proc.stdin.write("citylife selftest\n")
    proc.stdin.flush()
    done.wait(900)
    proc.stdin.write("stop\n")
    proc.stdin.flush()
    try:
        proc.wait(240)
    except subprocess.TimeoutExpired:
        proc.kill()
    for line in lines:
        if "SELFTEST" in line or "самотест" in line or "City Life тест" in line:
            print(line.rstrip())
    ok = any("SELFTEST DONE" in l and "FAILED" not in l for l in lines)
    print("ИТОГ:", "все тесты прошли" if ok else "есть падения")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
