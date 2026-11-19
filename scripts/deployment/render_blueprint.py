"""Render an offline Blueprint candidate from a verified release record; never deploy."""

import argparse
import json
from pathlib import Path
import re


def render(record, template, repository):
    if not re.fullmatch(r"ghcr\.io/[a-z0-9][a-z0-9_.-]*/[a-z0-9][a-z0-9_.-]*", repository):
        raise ValueError("Expected a specific GHCR repository")
    commit = record.get("commit", "")
    if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Expected a complete source commit")
    images = record.get("images", [])
    if not isinstance(images, list) or len(images) != 1 or not isinstance(images[0], str) or not re.fullmatch(
            re.escape(repository) + r"@sha256:[0-9a-f]{64}", images[0]):
        raise ValueError("Expected one immutable release digest for the approved repository")
    if record.get("repository", repository) != repository:
        raise ValueError("Release repository mismatch")
    result, count = re.subn(r"(?m)^(\s+url: )ghcr\.io/[^\s]+$",
                            lambda match: match[1] + images[0], template)
    if count != 1:
        raise ValueError("Blueprint must have exactly one GHCR image URL")
    result, count = re.subn(r"(?m)^# Image source commit: [0-9a-f]{40}$",
                            "# Image source commit: " + commit, result)
    if count != 1:
        raise ValueError("Blueprint source commit marker is missing or ambiguous")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("record", type=Path)
    parser.add_argument("template", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--repository", default="ghcr.io/tlavu2004/moodly-backend")
    args = parser.parse_args()
    # Parse and validate everything before creating/replacing the output.
    result = render(json.loads(args.record.read_text(encoding="utf-8")),
                    args.template.read_text(encoding="utf-8"), args.repository)
    if args.output.resolve() in {args.record.resolve(), args.template.resolve()}:
        raise ValueError("Candidate output must not overwrite its inputs")
    args.output.write_text(result, encoding="utf-8", newline="\n")
    print(f"Wrote offline Render candidate: {args.output}")


if __name__ == "__main__":
    main()
