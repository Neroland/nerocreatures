#!/usr/bin/env python3
"""Guard: no pointers into private planning records in this public repository.

Design notes, review findings and build process stay out of the repo. A comment that cites a record a
reader can never open (a numbered design decision, a build stage or a review finding, a planning file,
a unit of delegated build work, an internal tool or repository) explains nothing, so code says *why*
in its own words instead. This check fails on any such pointer in a tracked (or new, not ignored) text file.

Two kinds of rule:

* **Shapes** (plain regular expressions below): numbered design decisions, capitalised build-stage
  numbers, lettered review findings. They name nothing, so they are spelled out.
* **Names** (salted SHA-256 hashes below): the private planning repository, its planning and review
  files, the naming of delegated build tasks, the hand-off vocabulary and the authoring tools.
  Writing those names here would publish the very thing the check guards, so only their hashes are stored. Every line is
  split into word tokens; each token, each separator-bounded piece of a compound token (with and without
  its trailing separator and trailing digits), a token followed by a quoted title and each pair of
  neighbouring tokens is hashed with the salt and looked up, folded to lower case or exactly as
  written. To add a name, hash ``SALT + name`` (lower case for ``FOLDED``) and append the hex digest; never add the name itself.

Lower-case ``stage 1`` stays allowed: it is game vocabulary (wool, nodule and boss stages).

Usage (from anywhere):
    python3 tools/check_private_refs.py           # report findings, exit 1 if any
    python3 tools/check_private_refs.py --check   # same; the spelling CI uses
"""

import argparse
import hashlib
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SELF = Path(__file__).resolve()

TEXT_SUFFIXES = {".java", ".py", ".md", ".json", ".gradle", ".yml", ".yaml", ".toml", ".properties",
                 ".txt", ".js", ".mcmeta", ".cfg", ".accesswidener", ".kts"}

SHAPES = [
    ("design decision", re.compile(r"\b[Dd]ecisions?[- ]?\d{3}\b|\bdecisions/\d")),
    ("build stage", re.compile(r"\bStage \d+\b|\bstage gate\b")),
    ("review finding", re.compile(r"\breview [MLHS]\d\b|\b[Rr]eview fixes\b")),
]

SALT = "nerocreatures/private-refs/v1:"

# Private names, compared after folding to lower case. The last entry is a synthetic canary for the
# self-test, not a real name.
FOLDED = {
    "50379b7f861d114d106991e70138af831a554659402d60fa7293ca0e4c4bc3cf",
    "c1a25cd738b60c3647e1d4dd26dd1d945a71d8257f4bb398637e78e33a806448",
    "cbc84ea26bb66b2ff340e4785de041101d134adc632de5b079b7081b5efc08e1",
    "bad023aa287c27225d942815308cc41aa5697e0b6d1b18a5c79c95db2d7fc110",
    "ed5dd98f0a0f2eb6a7ddb0be04ca5ee6f8c76bef3edc8e1ce4e4c7f60e091038",
    "7cfa15d38e9d010b041288d5f178a75fb8c5249de3e42354c34fe96b68ccaf4c",
    "f8b005024c99a6a804de0852052c39e13fd3831d26ab46dac5df11cb601caf03",
    "9d465f9e778ac1c860453628a0efda3b6f2384661394b19dd4b437f790324fd9",
    "4b0b8857a467e7a6bdafb1ac0c6b563893ef82cfb0870da5b099d8047991954d",
    "64023a10c16cdd49b54111f67ddd8748589eaac7a76c881608ea7d32a5614adb",
    "b8d529067f35835a02f339eb29389f620346356ba5fd1a86999ba255632592e2",
    "ac42778accd4702b98f8da0809f4143ff041b47c30d140e5fa4689b6130e2a04",
    "5dedc83f62da6c36afe88ca7ebb290616b0cc85b716853256227707817815959",
    "15d51c0141f323c1f737f175763313c43cc2632375a26d5f7120e8a25e36e8c4",
}

# Private names compared exactly as written (their lower-case forms are ordinary English).
EXACT = {
    "2e558448c0fff9137dfc4022122dfe5a952ff94a8e8badccf7c9a512634291eb",
    "bc65bfe002dd59b9758727557c3795c85322140a9419340a3c3ad35b797d10cb",
    "213d14fe72ad6d90ebf5a2f40c8dd6e9bbdf31330ff21aaa6a0af6a09143c7d6",
    "18b1a8a4a54cfdc0c95f2b68f74ac850ea80bc157bb05e929766a308e7837cfc",
    "0dc82e80396ef1ebc6ae66114134837c0027204fdc379c411b03f28cd0e1a96c",
    "366f481c4723ccca691307e20c132ac8072dd85953cecf1c27cc4ddd5003ed2c",
}

TOKEN = re.compile(r"[A-Za-z0-9_]+(?:[-./][A-Za-z0-9_]+)*[-./]?")
PART = re.compile(r"([A-Za-z0-9_]+)([-./]?)")


def _digest(text):
    return hashlib.sha256((SALT + text).encode("utf-8")).hexdigest()


def _pieces(token):
    """Every run of whole parts of a compound token, with and without its trailing separator and digits."""
    parts = PART.findall(token)
    for i in range(len(parts)):
        joined = ""
        for j in range(i, len(parts)):
            word, sep = parts[j]
            yield joined + word
            stripped = word.rstrip("0123456789")
            if stripped and stripped != word:
                yield joined + stripped
            joined += word + sep
            if sep:
                yield joined


def _candidates(line):
    tokens = []
    for match in TOKEN.finditer(line):
        token = match.group(0)
        tokens.append(token)
        yield from _pieces(token)
        if line.startswith(' "', match.end()):
            # A token followed by a quoted title, the way a checklist entry is cited.
            yield token + ' "'
    for first, second in zip(tokens, tokens[1:]):
        yield first.rstrip("-./") + " " + second.rstrip("-./")


def name_hit(line):
    """The first private name in the line, or None. Returns the matched text from the line itself."""
    for candidate in _candidates(line):
        if _digest(candidate.lower()) in FOLDED or _digest(candidate) in EXACT:
            return candidate
    return None


def scan_text(text, label):
    findings = []
    for line_no, line in enumerate(text.splitlines(), 1):
        for name, pattern in SHAPES:
            hit = pattern.search(line)
            if hit:
                findings.append(f"{label}:{line_no}: {name} reference '{hit.group(0)}'")
                break
        else:
            hit = name_hit(line)
            if hit:
                findings.append(f"{label}:{line_no}: private name reference '{hit}'")
    return findings


def tracked_files():
    """Tracked plus new (not ignored) files, so a fresh file cannot slip past before its first commit."""
    out = subprocess.run(["git", "ls-files", "-co", "--exclude-standard"], cwd=REPO,
                         capture_output=True, text=True, check=True).stdout
    for line in out.splitlines():
        path = REPO / line
        if path.suffix.lower() in TEXT_SUFFIXES and path.is_file() and path.resolve() != SELF:
            yield path


# Synthetic samples only: the shapes, and the hash lookup through the canary name.
SELF_TEST = [
    ("// griefing gate (decision 123)", True),
    (" * as decisions 101 and 102 say", True),
    ("see decisions/101-example.md", True),
    ("/** Stage 9 example. */", True),
    ("(Stage 1 review L9)", True),
    ("see ZZ-Canary-Ref for why", True),
    ("../zz-canary-ref/notes", True),
    ("zz-canary-ref.md", True),
    ("wool stage 0 is shorn; stage 3 is ready to shear", False),
    ("decisionTree = 42; // a decision made per tick", False),
    ("the griefing gate decides; the owner is the player who tamed it", False),
    ("zz-canary is not the whole name", False),
]


def self_test():
    failures = []
    for snippet, expect in SELF_TEST:
        if bool(scan_text(snippet, "self-test")) != expect:
            failures.append(f"self-test: expected {'a finding' if expect else 'no finding'} for: {snippet!r}")
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="exit 1 on any finding (default behaviour)")
    parser.add_argument("paths", nargs="*", help="scan these files instead of the repository")
    args = parser.parse_args()
    broken = self_test()
    if broken:
        print("\n".join(broken))
        return 2
    findings = []
    checked = 0
    paths = [Path(p).resolve() for p in args.paths] if args.paths else list(tracked_files())
    for path in paths:
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        checked += 1
        try:
            label = path.relative_to(REPO).as_posix()
        except ValueError:
            label = path.as_posix()
        findings.extend(scan_text(text, label))
    for finding in findings:
        print(finding)
    if findings:
        print(f"check_private_refs: {len(findings)} private reference(s) in {checked} file(s)")
        return 1
    print(f"check_private_refs: ok ({checked} text files, no private references)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
