#!/usr/bin/env python3
"""Wiki checker: the public wiki says exactly what the code does.

    python3 -B tools/check_wiki.py           # report (exit 1 on any problem)
    python3 -B tools/check_wiki.py --check   # same; the form CI uses

Checks:

- **Pages**: every required page exists and ``Home.md`` links it; every link from one wiki page to
  another points at a page that exists.
- **Guard**: no wiki page names the private planning repository or the agent context files
  (``AGENTS.md``, ``CLAUDE.md``); the published wiki is a separate repo and cannot reach them.
- **Config**: the keys in ``Config.md`` are exactly the keys ``config/NeroCreaturesConfig`` declares,
  and every table row's default and range match the code.
- **Commands**: every runnable ``/nerocreatures`` path in ``command/`` is documented in
  ``Commands.md``, every ``/ncgallery`` path of the client capture harness in ``Gallery.md``, and every
  ``/nerocreatures`` or ``/ncgallery`` command written anywhere in the wiki exists in the code.

Standard library only.
"""
from __future__ import annotations

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WIKI = os.path.join(ROOT, "wiki")
JAVA = os.path.join(ROOT, "common", "src", "main", "java", "za", "co", "neroland", "nerocreatures")

REQUIRED_PAGES = [
    "Luna", "Mars", "Cindara", "Glacira", "Greenxertz", "Nyxara", "Asteroid", "Wrecks-and-Pirates",
    "Deep-Space", "Mounts", "Livestock-and-Breeding", "Traits", "Field-Scanner-and-Bestiary",
    "Habitat-Blocks", "Gear", "Bosses", "Commands", "Config", "Link-Module", "Privacy", "Menagerie",
]

# Names the published wiki must never carry (split so this file does not match itself).
GUARD = ["neroland" + "-mc-" + "ecosystem", "AGENTS" + ".md", "CLAUDE" + ".md"]

# camelCase words in Config.md that are not this mod's keys.
NOT_KEYS = {"mobGriefing"}


def read(path: str) -> str:
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def wiki_pages() -> dict:
    return {n[:-3]: read(os.path.join(WIKI, n)) for n in sorted(os.listdir(WIKI)) if n.endswith(".md")}


# ---------------------------------------------------------------------------------------- config

def config_keys() -> dict:
    """key -> (default, min, max) as strings; min/max are None for booleans."""
    text = read(os.path.join(JAVA, "config", "NeroCreaturesConfig.java"))
    keys = {}
    for kind, args in re.findall(r"SCHEMA\.(\w+)\(\s*(\"\w+\"[^;]*?)\"", text, re.S):
        parts = [p.strip() for p in args.split(",")]
        key = parts[0].strip('"')
        if kind == "bool":
            keys[key] = (parts[1], None, None)
        else:
            keys[key] = (parts[1], parts[2], parts[3])
    return keys


def number(text: str) -> float:
    return float(text.rstrip("DdFfLl"))


def check_config(pages: dict, fail) -> int:
    keys = config_keys()
    text = pages.get("Config", "")
    documented = set()
    for token in re.findall(r"`([a-z][a-z0-9]*[A-Z]\w*)`", text):
        if token in NOT_KEYS:
            continue
        documented.add(token)
        if token not in keys:
            fail("Config.md: `%s` is not a key in NeroCreaturesConfig" % token)
    for key in keys:
        if key not in documented:
            fail("Config.md: key `%s` is missing" % key)
    for row in re.findall(r"^\|\s*`(\w+)`\s*\|([^|]*)\|([^|]*)\|", text, re.M):
        key, default, rng = row[0], row[1].strip(), row[2].strip()
        if key not in keys:
            continue
        code_default, low, high = keys[key]
        if low is None:
            if default != code_default:
                fail("Config.md: `%s` default %s, code says %s" % (key, default, code_default))
            continue
        try:
            if number(default) != number(code_default):
                fail("Config.md: `%s` default %s, code says %s" % (key, default, code_default))
            bounds = re.fullmatch(r"(-?[\d.]+)\s*-\s*(-?[\d.]+)", rng)
            if not bounds or number(bounds.group(1)) != number(low) or number(bounds.group(2)) != number(high):
                fail("Config.md: `%s` range '%s', code says %s-%s" % (key, rng, low, high))
        except ValueError:
            fail("Config.md: `%s` row is not numeric (%s | %s)" % (key, default, rng))
    return len(keys)


# -------------------------------------------------------------------------------------- commands

TOKEN = re.compile(r'"(?:[^"\\]|\\.)*"|\.then\(|\.executes\(|literal\("(\w[\w-]*)"\)|'
                   r'argument\("(\w+)"|\b(\w+)Argument\(\)|BudgetCommand\.node\(\)|\(|\)')


class Node:
    def __init__(self, name: str) -> None:
        self.name = name
        self.children = []
        self.runs = False


def parse_tree(text: str, splices: dict) -> Node:
    """A Brigadier builder chain (from just inside ``register(`` or ``return``) to a command tree."""
    root = Node("")
    frames = [[0, root, None]]  # [paren depth, parent node, this frame's node]
    depth = 0
    for match in TOKEN.finditer(text):
        tok = match.group(0)
        if tok.startswith('"'):
            continue
        if tok == ".then(":
            depth += 1
            frames.append([depth, frames[-1][2], None])
        elif tok == ".executes(":
            if frames[-1][2] is not None and depth == frames[-1][0]:
                frames[-1][2].runs = True
            depth += 1
        elif match.group(1) or match.group(2):
            frame = frames[-1]
            if frame[2] is None and depth == frame[0]:
                name = match.group(1) or "<%s>" % match.group(2)
                frame[2] = Node(name)
                frame[1].children.append(frame[2])
            if match.group(1):
                pass  # literal("x") closes its own paren in the regex
            else:
                depth += 1  # argument("x", ...) leaves its paren open
        elif match.group(3):
            # A helper such as playerArgument() returns an argument("player", ...) builder.
            frame = frames[-1]
            if frame[2] is None and depth == frame[0]:
                frame[2] = Node("<%s>" % match.group(3))
                frame[1].children.append(frame[2])
        elif tok == "BudgetCommand.node()":
            frame = frames[-1]
            if frame[2] is None and depth == frame[0]:
                frame[2] = splices["budget"]
                frame[1].children.append(frame[2])
        elif tok == "(":
            depth += 1
        elif tok == ")":
            depth -= 1
            while len(frames) > 1 and depth < frames[-1][0]:
                frames.pop()
            if depth < 0:
                break
    return root


def runnable_paths(node: Node, prefix: str = "") -> list:
    out = []
    for child in node.children:
        path = (prefix + " " + child.name).strip()
        if child.runs:
            out.append(path)
        out.extend(runnable_paths(child, path))
    return out


def segment(text: str, start: str) -> str:
    i = text.index(start) + len(start)
    return text[i:]


def code_commands() -> tuple:
    budget_src = read(os.path.join(JAVA, "command", "BudgetCommand.java"))
    budget = parse_tree(segment(budget_src, "node() {\n        return ").replace("\r", ""), {}).children[0]
    main_src = read(os.path.join(JAVA, "command", "CreatureCommands.java"))
    main = parse_tree(segment(main_src, "dispatcher.register("), {"budget": budget})
    client_src = read(os.path.join(JAVA, "client", "GalleryCaptureHarness.java"))
    client = parse_tree(segment(client_src, "dispatcher.register("), {})
    return ["/" + p for p in runnable_paths(main)], ["/" + p for p in runnable_paths(client)]


def expand(command: str) -> list:
    """``a [b] [c]`` -> every spelling with or without each optional part; ``[x]`` becomes ``<x>``."""
    forms = [[]]
    for word in command.split():
        if word.startswith("[") and word.endswith("]"):
            forms = forms + [f + ["<%s>" % word[1:-1]] for f in forms]
        else:
            forms = [f + [word] for f in forms]
    return [" ".join(f) for f in forms]


def shape(path: str) -> str:
    """Argument names do not matter: ``<n>`` and ``<stop>`` are the same slot."""
    return " ".join("<>" if w.startswith("<") else w for w in path.split())


def documented(text: str) -> set:
    found = set()
    for cmd in re.findall(r"`(/(?:nerocreatures|ncgallery)\b[^`]*)`", text):
        found.add(cmd.strip())
    for cmd in re.findall(r"^(/(?:nerocreatures|ncgallery)\b.*?)(?:\s{2,}.*)?$", text, re.M):
        found.add(cmd.strip())
    return found


def check_commands(pages: dict, fail) -> int:
    server, client = code_commands()
    if not server or not client:
        fail("commands: could not read the command trees from the code")
        return 0
    known = set()
    prefixes = set()
    for path in server + client:
        words = shape(path).split()
        known.add(" ".join(words))
        for i in range(1, len(words) + 1):
            prefixes.add(" ".join(words[:i]))
    # Every command written in the wiki resolves (a bare tree root or a partial path is fine in prose).
    for page, text in pages.items():
        for cmd in documented(text):
            for form in expand(cmd):
                words = form.split()
                literal_shape = " ".join("<>" if w.startswith("<") else w for w in words)
                if literal_shape in prefixes:
                    continue
                # A concrete example (`summon-boss cinder_tyrant`) fills an argument slot.
                ok = False
                for path in prefixes:
                    pw = path.split()
                    if len(pw) == len(words) and all(p == w or p == "<>" for p, w in zip(pw, words)):
                        ok = True
                        break
                if not ok:
                    fail("%s.md: `%s` is not a command in the code" % (page, form))
    # Every runnable path is documented on its page.
    for paths, page in ((server, "Commands"), (client, "Gallery")):
        shapes = set()
        for cmd in documented(pages.get(page, "")):
            shapes.update(shape(f) for f in expand(cmd))
        for path in paths:
            if shape(path) not in shapes:
                fail("%s.md: runnable command `%s` is not documented" % (page, path))
    return len(server) + len(client)


# ----------------------------------------------------------------------------------------- pages

def check_pages(pages: dict, fail) -> None:
    home = pages.get("Home", "")
    home_links = set(re.findall(r"\]\(([\w-]+)\.md(?:#[\w-]*)?\)", home))
    for page in REQUIRED_PAGES:
        if page not in pages:
            fail("wiki: required page %s.md is missing" % page)
        elif page not in home_links:
            fail("Home.md: does not link %s.md" % page)
    for name, text in pages.items():
        for target in re.findall(r"\]\(([\w-]+)\.md(?:#[\w-]*)?\)", text):
            if target not in pages:
                fail("%s.md: links to missing page %s.md" % (name, target))
        for word in GUARD:
            if word in text:
                fail("%s.md: names %s (wiki guard)" % (name, word))


def main(argv: list) -> int:
    problems = []
    pages = wiki_pages()
    check_pages(pages, problems.append)
    nkeys = check_config(pages, problems.append)
    ncmds = check_commands(pages, problems.append)
    for p in problems:
        print("FAIL " + p)
    print("wiki check: %d pages, %d config keys, %d runnable commands; %s"
          % (len(pages), nkeys, ncmds, "%d problem(s)" % len(problems) if problems else "ok"))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
