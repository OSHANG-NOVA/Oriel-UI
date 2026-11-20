#!/usr/bin/env python3
"""Resolve Stonecutter conditional comments for the MC 1.20.1 target.

Directive forms found in this project (measured; all six occur):

    //? if >=26.3 {            block open (guard)
    //? if >=26.2 <26.3 {      block open, several space-separated guards = AND
    //? if <26.3               ONE-SHOT: governs the next content line only
    //?} else {                close the if branch, open the else branch
    //?} else if >=26.2 {      close, open an else-if branch
    //?} else                  ONE-SHOT else: governs the next content line only
    //?}                       close the block
    *///?}                     close, where the dead branch's `/*` block ends

Stonecutter comments out every branch that does not match the version being
built: a multi-line dead branch opens with `/*` on its first content line and
the matching `*/` is glued to the closing directive (`*///?}`); a one-line dead
branch is `//code` or `/*code*/`.

For a 1.20.1 target every `>=26.x` guard is false and every `<26.x` guard is
true, so exactly one branch survives. The branch that survives may itself be the
commented-out one (that is the whole point of retargeting), so the resolver
decides per branch whether its body needs un-commenting by looking at the first
content line: a body starting with `/*` was commented out.
"""

import re
import sys
from pathlib import Path

TARGET = (1, 20, 1)
VERSION = re.compile(r"^(\d+)(?:\.(\d+))?(?:\.(\d+))?$")


def parse_version(text):
    m = VERSION.match(text.strip())
    return tuple(int(g) if g else 0 for g in m.groups()) if m else None


def eval_atom(term):
    term = term.strip()
    if not term:
        return False
    if term.startswith("!"):
        return not eval_atom(term[1:])
    m = re.match(r"^(>=|<=|>|<|==|=)?\s*(\S+)$", term)
    op, ver = (m.group(1) or "="), m.group(2)
    c = parse_version(ver)
    if c is None:
        return False  # non-version guard (feature flag): unknown -> false
    if op == ">=":
        return TARGET >= c
    if op == "<=":
        return TARGET <= c
    if op == ">":
        return TARGET > c
    if op == "<":
        return TARGET < c
    return TARGET == c


def eval_cond(cond):
    """Space-separated guards are AND; `||` splits OR groups."""
    cond = cond.strip()
    if not cond:
        return False
    for group in cond.split("||"):
        terms = [t for t in group.split() if t]
        if terms and all(eval_atom(t) for t in terms):
            return True
    return False


def split_directive(line):
    i = line.find("//?")
    if i < 0:
        return None
    return line[:i], line[i + 3:].strip()


def uncomment(line, state):
    """Turn one Stonecutter-commented line back into live code.

    A dead branch that is more than one line long is a real block comment:
    its first line opens with `/*` and the matching `*/` sits on the last one
    (usually glued to the closing directive, already stripped). Middle lines of
    such a block belong to the comment and must be un-commented too, which is
    what `state["open"]` tracks across calls.
    """
    s = line
    if state["open"]:
        body = s
        close = body.rfind("*/")
        if close >= 0:
            state["open"] = False
            body = body[:close] + body[close + 2:]
        return body.rstrip()

    m = re.match(r"^(\s*)/\*(.*)$", s)
    if m:
        indent, body = m.group(1), m.group(2)
        close = body.rfind("*/")
        if close >= 0:
            body = body[:close] + body[close + 2:]
        else:
            state["open"] = True
        return (indent + body).rstrip()

    m = re.match(r"^(\s*)//(.*)$", s)
    if m:
        return m.group(1) + m.group(2)
    return s


def unescape_comments(text):
    """Undo Stonecutter's `/*` -> `/^*` and `*/` -> `^/` escaping.

    Stonecutter cannot nest block comments, so when a branch that is already
    commented out contains a Javadoc it rewrites the inner delimiters. Once the
    surrounding branch is gone the escapes are just noise; leaving them in
    produces `/*`-free nonsense like `/^* ... ^/`.

    Only `^/` at the end of a line is rewritten: a regex such as
    `assets/[^/]+/ui/.*` also contains the character pair and must survive.
    """
    text = text.replace("/^*", "/*")
    text = re.sub(r"\^/(\s*)$", r"*/\1", text, flags=re.MULTILINE)
    return text


class Frame:
    __slots__ = ("taken", "emitting", "commented", "cstate")

    def __init__(self, taken):
        self.taken = taken
        self.emitting = taken
        self.commented = None  # decided by the first content line
        self.cstate = {"open": False}

    def open_else(self):
        if self.taken:
            self.emitting = False
        else:
            self.taken = True
            self.emitting = True
            self.commented = None
            self.cstate = {"open": False}

    def open_else_if(self, cond):
        if self.taken:
            self.emitting = False
        else:
            ok = eval_cond(cond)
            self.taken = ok
            self.emitting = ok
            self.commented = None
            self.cstate = {"open": False}


def process(path):
    text = path.read_text(encoding="utf-8")
    if "//?" not in text:
        return False

    lines = text.split("\n")
    out = []
    stack = []
    oneshot = None  # True/False for the next content line

    for raw in lines:
        hit = split_directive(raw)
        if hit is not None:
            _prefix, d = hit

            if d.startswith("if "):
                body = d[3:].strip()
                if body.endswith("{"):
                    stack.append(Frame(eval_cond(body[:-1])))
                else:
                    oneshot = eval_cond(body)
                continue

            if d.startswith("}"):
                rest = d[1:].strip()
                if not rest:
                    if stack:
                        stack.pop()
                    continue
                if rest.startswith("else if "):
                    body = rest[8:].strip()
                    if body.endswith("{"):
                        if stack:
                            stack[-1].open_else_if(body[:-1])
                    else:
                        # one-line else-if: decide it, then leave the block
                        ok = eval_cond(body.rstrip("}"))
                        frame = stack.pop() if stack else None
                        oneshot = ok if frame is not None and not frame.taken else False
                    continue
                if rest.startswith("else"):
                    body = rest[4:].strip()
                    if body.startswith("{"):
                        if stack:
                            stack[-1].open_else()
                    else:
                        # one-line else: applies to the next content line
                        frame = stack.pop() if stack else None
                        oneshot = (not frame.taken) if frame is not None else False
                    continue
                continue

            if d.startswith("else if "):
                body = d[8:].strip()
                if body.endswith("{"):
                    if stack:
                        stack[-1].open_else_if(body[:-1])
                continue

            if d.startswith("else"):
                body = d[4:].strip()
                if body.startswith("{"):
                    if stack:
                        stack[-1].open_else()
                else:
                    frame = stack.pop() if stack else None
                    oneshot = (not frame.taken) if frame is not None else False
                continue

            continue

        if not raw.strip():
            if all(f.emitting for f in stack) and oneshot is None:
                out.append(raw)
            continue

        if oneshot is not None:
            if oneshot:
                out.append(uncomment(raw, {"open": False}))
            oneshot = None
            continue

        if not all(f.emitting for f in stack):
            continue

        frame = stack[-1] if stack else None
        if frame is not None and frame.commented is None:
            frame.commented = raw.lstrip().startswith("/*")
        out.append(uncomment(raw, frame.cstate) if (frame is not None and frame.commented) else raw)

    cleaned, blanks = [], 0
    for line in out:
        if line.strip() == "":
            blanks += 1
            if blanks > 2:
                continue
        else:
            blanks = 0
        cleaned.append(line)

    new_text = unescape_comments("\n".join(cleaned))
    if new_text != text:
        path.write_text(new_text, encoding="utf-8")
        return True
    return False


def main(argv):
    root = Path(argv[1]) if len(argv) > 1 else Path("src/main/java")
    changed = [p for p in root.rglob("*.java") if process(p)]
    for p in changed:
        print(f"resolved {p}")
    print(f"\n{len(changed)} file(s) changed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
