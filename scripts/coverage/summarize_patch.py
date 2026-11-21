import os
import re
import subprocess
from collections import defaultdict
from pathlib import Path
import xml.etree.ElementTree as ET

REPORT = Path("target/site/jacoco/jacoco.xml")


def coverage_rows(changes):
    root = ET.parse(REPORT).getroot()
    for package in root.findall("package"):
        for source in package.findall("sourcefile"):
            path = f"src/main/java/{package.attrib['name']}/{source.attrib['name']}"
            changed = changes.get(path, ())
            for line in source.findall("line"):
                if int(line.attrib["nr"]) in changed:
                    covered_branches = int(line.attrib["cb"])
                    yield (int(line.attrib["ci"]) > 0, covered_branches,
                           covered_branches + int(line.attrib["mb"]))


HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")


def changed_lines(base):
    diff = subprocess.check_output(
        ["git", "-c", "core.quotePath=false", "diff", "--no-ext-diff", "--no-color",
         "--find-renames", "--unified=0", "--dst-prefix=b/", f"{base}...HEAD", "--"],
        encoding="utf-8",
    )
    files = defaultdict(set)
    path = None
    in_header = False
    for line in diff.splitlines():
        if line.startswith("diff --git "):
            path = None
            in_header = True
        elif in_header and line.startswith("+++ b/"):
            path = line[6:].split("\t", 1)[0]
            in_header = False
        elif path and (match := HUNK.match(line)):
            start = int(match[1])
            count = 1 if match[2] is None else int(match[2])
            files[path].update(range(start, start + count))
    return files


def main():
    lines = ["## Coverage on changed lines", "",
             "Advisory. Counts include changed source lines present in the coverage report.", ""]
    if not REPORT.is_file():
        lines.append("Coverage report unavailable.")
    else:
        changes = changed_lines(os.environ["PR_BASE_SHA"])
        coverage = list(coverage_rows(changes))
        lines.extend(["| Metric | Covered | Total | Coverage | Target | Result |",
                      "| --- | ---: | ---: | ---: | ---: | --- |"])
        for name, target, covered, total in (
            ("LINE", 85, sum(row[0] for row in coverage), len(coverage)),
            ("BRANCH", 75, sum(row[1] for row in coverage), sum(row[2] for row in coverage)),
        ):
            percent = f"{covered / total:.2%}" if total else "N/A"
            result = "N/A" if not total else ("Pass" if covered * 100 >= target * total else "Below target")
            lines.append(f"| {name} | {covered} | {total} | {percent} | {target}% | {result} |")
    summary = "\n".join(lines) + "\n"
    print(summary, end="")
    with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as output:
        output.write(summary)


if __name__ == "__main__":
    main()
