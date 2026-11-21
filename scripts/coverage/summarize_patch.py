import os
import re
import subprocess
from collections import defaultdict
from pathlib import Path
import xml.etree.ElementTree as ET

REPORT = Path("target/site/jacoco/jacoco.xml")


def coverage_rows(root, changes):
    for package in root.findall("package"):
        for source in package.findall("sourcefile"):
            path = f"src/main/java/{package.attrib['name']}/{source.attrib['name']}"
            changed = changes.get(path, ())
            for line in source.findall("line"):
                if int(line.attrib["nr"]) in changed:
                    covered_branches = int(line.attrib["cb"])
                    instructions = int(line.attrib["ci"])
                    yield (instructions > 0, covered_branches,
                           covered_branches + int(line.attrib["mb"]), instructions,
                           instructions + int(line.attrib["mi"]))


def method_counts(root, changes):
    sources = [
        path for path, lines in changes.items()
        if lines and path.startswith("src/main/java/") and path.endswith(".java")
    ]
    if not sources:
        return 0, 0
    ranges = defaultdict(list)
    output = subprocess.check_output(
        ["java", "scripts/coverage/MethodRanges.java", *sources], encoding="utf-8"
    )
    for row in output.splitlines():
        path, name, start, end = row.split("\t")
        ranges[path, name].append((int(start), int(end)))

    covered = total = 0
    for package in root.findall("package"):
        for cls in package.findall("class"):
            path = f"src/main/java/{package.attrib['name']}/{cls.get('sourcefilename', '')}"
            changed = changes.get(path, ())
            for method in cls.findall("method"):
                entry = int(method.get("line", 0))
                candidates = [(start, end) for start, end in ranges[path, method.attrib["name"]]
                              if start <= entry <= end]
                if not candidates:
                    continue
                start, end = min(candidates, key=lambda span: span[1] - span[0])
                if any(start <= line <= end for line in changed):
                    counter = method.find("counter[@type='METHOD']")
                    if counter is not None:
                        covered += int(counter.attrib["covered"])
                        total += int(counter.attrib["covered"]) + int(counter.attrib["missed"])
    return covered, total


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
             "Advisory. Counts use changed executable lines and source methods that overlap the diff.", ""]
    if not REPORT.is_file():
        lines.append("Coverage report unavailable.")
    else:
        changes = changed_lines(os.environ["PR_BASE_SHA"])
        root = ET.parse(REPORT).getroot()
        coverage = list(coverage_rows(root, changes))
        lines.extend(["| Metric | Covered | Total | Coverage | Target | Result |",
                      "| --- | ---: | ---: | ---: | ---: | --- |"])
        for name, target, covered, total in (
            ("LINE", 85, sum(row[0] for row in coverage), len(coverage)),
            ("BRANCH", 75, sum(row[1] for row in coverage), sum(row[2] for row in coverage)),
            ("METHOD", 85, *method_counts(root, changes)),
            ("INSTRUCTION", 85, sum(row[3] for row in coverage), sum(row[4] for row in coverage)),
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
