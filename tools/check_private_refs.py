#!/usr/bin/env python3
"""Guard: no pointers into private planning records in this public repository.

Design notes, review findings and build process stay out of the repo. A comment that cites a record a
reader can never open ("decision 006", "Stage 2 review M1", a work-package or tool name) explains
nothing, so code says *why* in its own words instead. This check fails on any such pointer in a
tracked (or new, not ignored) text file:

* numbered design decisions: ``decision 006``, ``decisions 003 and 004``, ``decisions/006``;
* build stages and their reviews: ``Stage 4``, ``Stage 2 review``, ``PROGRESS "...``;
* work packages and their briefs: ``WP-Luna``, ``WP-BRIEF``, ``BUILD-PROMPT``, ``fragments/``;
* the build's review files and the authoring tools' names: ``AUDIT-0.5.0``, ``REVIEW-STAGE7``,
  ``PROGRESS.md``, ``Opus``, ``Fable``, ``Codex``;
* the integration hand-off vocabulary: ``the integrator``, ``integration pass``, ``entity contract``;
* the private planning repository's name.

Lower-case ``stage 1`` stays allowed: it is game vocabulary (wool, nodule and boss stages).

Usage (from anywhere):
    python3 tools/check_private_refs.py           # report findings, exit 1 if any
    python3 tools/check_private_refs.py --check   # same; the spelling CI uses
"""

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SELF = Path(__file__).resolve()

TEXT_SUFFIXES = {".java", ".py", ".md", ".json", ".gradle", ".yml", ".yaml", ".toml", ".properties",
                 ".txt", ".js", ".mcmeta", ".cfg", ".accesswidener", ".kts"}

# The planning repository's name, split so this file does not match itself if it is ever scanned.
PLANNING_REPO = "neroland" + "-mc-" + "ecosystem"

PATTERNS = [
    ("design decision", re.compile(r"\b[Dd]ecisions?[- ]?\d{3}\b|\bdecisions/\d")),
    ("build stage", re.compile(r"\bStage \d+\b|\bPROGRESS \"|\bstage gate\b")),
    ("review finding", re.compile(r"\breview [MLHS]\d\b|\b[Rr]eview fixes\b")),
    ("work package", re.compile(r"\bWP-[A-Za-z]|\bwork packages?\b|\bfragments/")),
    ("planning file", re.compile(r"BUILD-PROMPT|PROGRESS\.md|\bAUDIT\b|AUDIT-|REVIEW-STAGE|ENTITY-CONTRACT")),
    ("authoring tool", re.compile(r"\b(?:Opus|Fable|Codex)\b")),
    ("integration hand-off", re.compile(r"\bthe integrator\b|\bintegration (?:pass|agent)\b|\b[Ee]ntity contract\b")),
    ("planning repository", re.compile(re.escape(PLANNING_REPO), re.IGNORECASE)),
]


def tracked_files():
    """Tracked plus new (not ignored) files, so a fresh file cannot slip past before its first commit."""
    out = subprocess.run(["git", "ls-files", "-co", "--exclude-standard"], cwd=REPO,
                         capture_output=True, text=True, check=True).stdout
    for line in out.splitlines():
        path = REPO / line
        if path.suffix.lower() in TEXT_SUFFIXES and path.is_file() and path.resolve() != SELF:
            yield path


def scan_text(text, label):
    findings = []
    for line_no, line in enumerate(text.splitlines(), 1):
        for name, pattern in PATTERNS:
            hit = pattern.search(line)
            if hit:
                findings.append(f"{label}:{line_no}: {name} reference '{hit.group(0)}'")
                break
    return findings


SELF_TEST = [
    ("// griefing gate (decision 006)", True),
    (" * as decisions 003 and 004 say", True),
    ("see decisions/004-mount-base.md", True),
    ("/** Stage 4 habitat blocks. */", True),
    ("(Stage 2 review M1)", True),
    ("<b>WP-Asteroid calls", True),
    ("first-pass layers (Opus); Fable may repaint", True),
    ("the integrator adds .setId(key)", True),
    ("per the entity contract", True),
    ("../" + PLANNING_REPO + "/nerocreatures", True),
    ("see AUDIT-0.5.0.md", True),
    ("wool stage 0 is shorn; stage 3 is ready to shear", False),
    ("decisionTree = 42; // a decision made per tick", False),
    ("the griefing gate decides; the owner is the player who tamed it", False),
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
    parser.parse_args()
    broken = self_test()
    if broken:
        print("\n".join(broken))
        return 2
    findings = []
    checked = 0
    for path in tracked_files():
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        checked += 1
        findings.extend(scan_text(text, path.relative_to(REPO).as_posix()))
    for finding in findings:
        print(finding)
    if findings:
        print(f"check_private_refs: {len(findings)} private reference(s) in {checked} file(s)")
        return 1
    print(f"check_private_refs: ok ({checked} text files, no private references)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
