#!/usr/bin/env python3
"""Privacy check for log calls (POPIA/GDPR): no player identity at INFO or above.

Scans every Java source in this repo (main and test source sets, all loaders) for logger calls at
INFO, WARN or ERROR (``LOGGER.info(...)``, ``LOG.warn(...)``, ``.error(...)``, ``.atInfo()`` ...) and
fails when an argument outside the string literals names something that identifies a player or a
place: UUIDs, owner ids, player objects or names, display or profile names, block positions or
coordinates. String literals are ignored (they are fixed text). A call that is genuinely safe can be
allowed with a trailing ``// log-privacy: ok <reason>`` comment on its first line.

Two further rules, because a name deny-list alone proves less than it seems to:

* A bare throwable argument (``e``, ``t``, ``ex``, ``error``, ...) is a finding. Its message is free text
  that can hold anything, and the crash reporter forwards ERROR lines with a throwable. Log
  ``e.getClass().getSimpleName()`` instead.
* Generic object names that often hold an identity (``id``, ``entity``, ``creature``, ``pet``,
  ``drone``, ``row``, ``stack``, ``raw``, ``source``) are findings unless a safe accessor follows them
  (``.getClass().getSimpleName()``, ``.size()``, ``.kind()``, ``.type()`` and the like).

CI runs this with ``--check`` on every publish.

DEBUG and TRACE are not checked: they are off on a normal server. They still must never carry
identity by convention, but this tool's contract is INFO and above.

Usage (from anywhere):
    python tools/check_log_privacy.py           # report findings, exit 1 if any
    python tools/check_log_privacy.py --check   # same; the spelling the gate uses
"""

import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SOURCE_ROOTS = ["common/src", "fabric/src", "forge/src", "neoforge/src"]

CALL = re.compile(r"\b(?:LOGGER|LOG|logger|log)\s*\.\s*(info|warn|error|atInfo|atWarn|atError)\s*\(")
ALLOW = re.compile(r"//\s*log-privacy:\s*ok\b")

# Identifiers that, appearing as (part of) an argument, put identity or position into a log line.
IDENTITY = re.compile(
    r"\b(getUUID|getStringUUID|uuid|UUID|ownerId|owner|ownerUuid|controllerUuid|formerOwner|"
    r"getGameProfile|getScoreboardName|getDisplayName|getName|getPlainTextName|player|players|"
    r"ServerPlayer|serverPlayer|subject|profile|"
    r"blockPosition|getOnPos|position|BlockPos|pos|getX|getY|getZ|x|y|z|chunkPos|ChunkPos|"
    r"getIpAddress|getRemoteAddress|address|chat)\b")

# A safe accessor: what follows it no longer carries the object itself.
SAFE_ACCESSOR = re.compile(r"\.\s*getClass\s*\(\s*\)\s*\.\s*get(?:Simple)?Name\s*\(\s*\)")
SAFE_AFTER = r"\s*\.\s*(?:SAFE\b|size\s*\(|length\s*\(|isEmpty\s*\(|kind\s*\(|type\s*\(|getType\s*\(|ordinal\s*\(|count\s*\()"

# Generic names that usually hold an identity-bearing object; fine only behind a safe accessor.
GENERIC = re.compile(r"\b(id|entity|creature|pet|drone|row|stack|raw|source)\b(?!" + SAFE_AFTER + ")")

# A whole argument that is just a throwable variable.
THROWABLE = re.compile(
    r"^\s*(e|t|ex|e2|err|error|exception|throwable|thrown|cause|failure|problem|\w+(?:Exception|Error|Throwable))\s*$")


def split_args(text):
    """Top-level comma-separated arguments of a call (literals already stripped)."""
    args = []
    depth = 0
    current = []
    for c in text:
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        if c == "," and depth == 0:
            args.append("".join(current))
            current = []
        else:
            current.append(c)
    args.append("".join(current))
    return args


def argument_finding(arg):
    """Why one log argument may carry identity, or None."""
    code = SAFE_ACCESSOR.sub(".SAFE", arg)
    if THROWABLE.match(code):
        return "bare throwable " + code.strip()
    if re.fullmatch(r"\s*\w+\s*\.SAFE\s*", code):
        return None
    hit = IDENTITY.search(code) or GENERIC.search(code)
    return hit.group(1) if hit else None


def strip_literals(text):
    """Remove string and char literals and comments, so only code remains."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if text.startswith('"""', i):
            end = text.find('"""', i + 3)
            i = n if end < 0 else end + 3
            out.append('""')
        elif c == '"' or c == "'":
            j = i + 1
            while j < n and text[j] != c:
                j += 2 if text[j] == "\\" else 1
            i = j + 1
            out.append('""')
        elif text.startswith("//", i):
            end = text.find("\n", i)
            i = n if end < 0 else end
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = n if end < 0 else end + 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def call_text(source, start):
    """The argument text of the call whose '(' is at start-1, up to the matching ')'."""
    depth = 1
    i = start
    n = len(source)
    in_str = None
    while i < n and depth:
        c = source[i]
        if in_str:
            if c == "\\":
                i += 2
                continue
            if c == in_str:
                in_str = None
        elif c in "\"'":
            in_str = c
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
        i += 1
    return source[start:i - 1]


def is_commented_out(source, pos):
    """Whether pos sits inside a block comment or after // on its line (stonecutter /* */ blocks)."""
    line_start = source.rfind("\n", 0, pos) + 1
    if "//" in source[line_start:pos]:
        return True
    opened = source.rfind("/*", 0, pos)
    closed = source.rfind("*/", 0, pos)
    return opened > closed


def scan_source(source, label):
    """Findings for one Java source text; returns (calls checked, findings)."""
    findings = []
    checked = 0
    for match in CALL.finditer(source):
        line_no = source.count("\n", 0, match.start()) + 1
        line_end = source.find("\n", match.start())
        first_line = source[match.start():line_end if line_end >= 0 else len(source)]
        args = call_text(source, match.end())
        checked += 1
        if ALLOW.search(first_line):
            continue
        for arg in split_args(strip_literals(args)):
            hit = argument_finding(arg)
            if hit:
                where = "commented-out (stonecutter) " if is_commented_out(source, match.start()) else ""
                findings.append(f"{label}:{line_no}: {where}{match.group(1)} call passes '{hit}'")
                break
    return checked, findings


def scan():
    findings = []
    checked = 0
    for root in SOURCE_ROOTS:
        base = REPO / root
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*.java")):
            count, found = scan_source(path.read_text(encoding="utf-8"), path.relative_to(REPO).as_posix())
            checked += count
            findings.extend(found)
    return checked, findings


# Synthetic calls the checker must flag (or pass); run before every scan so a regex edit that
# silently stops matching fails the check instead of passing everything.
SELF_TEST = [
    ('LOGGER.info("Released {}", player.getUUID());', True),
    ('LOGGER.warn("Owner {} lost a pet", ownerId);', True),
    ('LOGGER.error("at {} {} {}", pos.getX(), pos.getY(), pos.getZ());', True),
    ('LOGGER.info("{} joined",\n        serverPlayer.getName().getString());', True),
    ('LOGGER.info("Erasure released {} creature(s) for player", freed);', False),
    ('LOGGER.warn("[x] {} failed: {}", menu, e.getClass().getSimpleName());', False),
    ('LOGGER.info("uuid {}", id); // log-privacy: ok synthetic', False),
    ('LOGGER.debug("{}", player.getUUID());', False),
    ('LOGGER.warn("[x] {} failed", topic, e);', True),
    ('LOGGER.error("[x] failed", exception);', True),
    ('LOGGER.warn("[x] failed: {}", failure.getMessage());', False),
    ('LOGGER.warn("[x] drone {} recalled", id);', True),
    ('LOGGER.info("[x] {}", entity);', True),
    ('LOGGER.info("[x] {}", creature.toString());', True),
    ('LOGGER.info("[x] {} rows", row);', True),
    ('LOGGER.info("[x] {}", stack);', True),
    ('LOGGER.info("[x] {}", raw);', True),
    ('LOGGER.info("[x] {}", source);', True),
    ('LOGGER.info("[x] {} pets", pet);', True),
    ('LOGGER.info("[x] {}", drone);', True),
    ('LOGGER.info("[x] {}", entity.getClass().getSimpleName());', False),
    ('LOGGER.info("[x] {} rows", rows.size());', False),
    ('LOGGER.info("[x] {}", row.kind());', False),
    ('LOGGER.warn("[x] failed: {}", e.getClass().getSimpleName());', False),
    ('LOGGER.warn("[x] {} failed: {}", eraser.getClass().getName(), e.getClass().getSimpleName());', False),
]


def self_test():
    failures = []
    for snippet, expect in SELF_TEST:
        _, found = scan_source(snippet, "self-test")
        if bool(found) != expect:
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
    checked, findings = scan()
    for finding in findings:
        print(finding)
    if findings:
        print(f"check_log_privacy: {len(findings)} log call(s) may carry identity "
              f"(of {checked} at INFO or above)")
        return 1
    print(f"check_log_privacy: ok ({checked} log calls at INFO or above, none carries identity)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
