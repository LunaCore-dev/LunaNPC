#!/usr/bin/env python3
# Comment-budget gate (see CLAUDE.md). Run before every push:
#   python scripts/check_comment_ratio.py
# Hard rule: the codebase-wide comment ratio must stay at or under MAX_OVERALL_PERCENT. A single
# complex/complicated file may sit higher when justified; those show in the per-file report and can
# carry a `comment-ratio-exempt` marker so they drop out of it. Exit code is non-zero on failure so a
# pre-push hook or CI step can block the push.

import os
import re
import sys

SOURCE_ROOTS = ["src/main/java", "src/client/java"]
MAX_OVERALL_PERCENT = 10.0
# Files reported (not failed) when a normal file drifts over this; small files are skipped because a
# single class doc legitimately dominates them.
FILE_REPORT_PERCENT = 10.0
FILE_REPORT_MIN_LINES = 40
EXEMPT_MARKER = "comment-ratio-exempt"


def classify(path):
    code = comment = 0
    in_block = False
    with open(path, encoding="utf-8", errors="replace") as handle:
        for raw in handle:
            line = raw.strip()
            if not line:
                continue
            if in_block:
                comment += 1
                if "*/" in line:
                    in_block = False
            elif line.startswith("//") or line.startswith("*"):
                comment += 1
            elif line.startswith("/*"):
                comment += 1
                if "*/" not in line:
                    in_block = True
            else:
                code += 1
    return code, comment


def is_exempt(path):
    with open(path, encoding="utf-8", errors="replace") as handle:
        return EXEMPT_MARKER in handle.read()


def main():
    total_code = total_comment = 0
    reported = []
    for root in SOURCE_ROOTS:
        for dirpath, _, filenames in os.walk(root):
            for name in filenames:
                if not name.endswith(".java"):
                    continue
                path = os.path.join(dirpath, name)
                code, comment = classify(path)
                total_code += code
                total_comment += comment
                lines = code + comment
                if lines == 0:
                    continue
                percent = 100.0 * comment / lines
                if (percent > FILE_REPORT_PERCENT and lines >= FILE_REPORT_MIN_LINES
                        and not is_exempt(path)):
                    reported.append((percent, comment, lines, path.replace("\\", "/")))

    total_lines = total_code + total_comment
    overall = 100.0 * total_comment / total_lines if total_lines else 0.0
    print(f"Comment ratio: {total_comment} / {total_lines} lines = {overall:.1f}% "
          f"(limit {MAX_OVERALL_PERCENT:.0f}%)")

    if reported:
        reported.sort(reverse=True)
        print(f"\nFiles over {FILE_REPORT_PERCENT:.0f}% (review - trim, or mark "
              f"'{EXEMPT_MARKER}' if the complexity justifies it):")
        for percent, comment, lines, path in reported:
            print(f"  {percent:5.1f}%  {comment:3d}/{lines:3d}  {path}")

    if overall > MAX_OVERALL_PERCENT:
        print(f"\nFAIL: codebase comment ratio {overall:.1f}% exceeds {MAX_OVERALL_PERCENT:.0f}%.")
        return 1
    print("\nOK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
