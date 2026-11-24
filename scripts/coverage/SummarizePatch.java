import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import org.w3c.dom.Element;

import javax.tools.ToolProvider;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class SummarizePatch {
    private static final Path REPORT = Path.of("target/site/jacoco/jacoco.xml");
    private static final String SOURCE_ROOT = "src/main/java/";
    private static final Pattern HUNK = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@");

    private record MethodKey(String path, String name) {}

    private record Span(long start, long end) {
        boolean contains(long line) {
            return start <= line && line <= end;
        }
    }

    private static final class Counter {
        long covered;
        long total;

        void add(int hits, int count) {
            covered += hits;
            total += count;
        }

        String row(String metric, int target) {
            String percent = total == 0 ? "N/A" : "%.2f%%".formatted(100.0 * covered / total);
            String result = total == 0 ? "N/A" : covered * 100 >= target * total ? "Pass" : "Below target";
            return "| %s | %d | %d | %s | %d%% | %s |\n".formatted(metric, covered, total, percent, target, result);
        }
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        var summary = new StringBuilder("## Coverage on changed lines\n\n")
                .append("Advisory. Counts use changed executable lines and source methods that overlap the diff.\n\n");
        if (Files.isRegularFile(REPORT)) {
            appendReport(summary, changedLines(System.getenv("PR_BASE_SHA")), baseClasses());
        } else {
            summary.append("Coverage report unavailable.\n");
        }
        System.out.print(summary);
        Files.writeString(Path.of(System.getenv("GITHUB_STEP_SUMMARY")), summary,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void appendReport(StringBuilder summary, Map<String, Set<Integer>> changes,
                                     Set<String> baseClasses) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var report = factory.newDocumentBuilder().parse(REPORT.toFile()).getDocumentElement();
        var lines = new Counter();
        var branches = new Counter();
        var methods = new Counter();
        var instructions = new Counter();
        var classes = new Counter();
        var uncoveredClasses = new ArrayList<String>();
        var ranges = methodRanges(changes);
        for (Element pkg : children(report, "package")) {
            String prefix = SOURCE_ROOT + pkg.getAttribute("name") + "/";
            for (Element source : children(pkg, "sourcefile")) {
                var changed = changes.getOrDefault(prefix + source.getAttribute("name"), Set.of());
                for (Element line : children(source, "line")) {
                    if (changed.contains(number(line, "nr"))) {
                        int coveredInstructions = number(line, "ci");
                        int coveredBranches = number(line, "cb");
                        lines.add(coveredInstructions > 0 ? 1 : 0, 1);
                        branches.add(coveredBranches, coveredBranches + number(line, "mb"));
                        instructions.add(coveredInstructions, coveredInstructions + number(line, "mi"));
                    }
                }
            }
            for (Element cls : children(pkg, "class")) {
                String className = cls.getAttribute("name");
                if (baseClasses != null && !baseClasses.contains(className)) {
                    countNewClass(cls, classes, uncoveredClasses);
                }
                String path = prefix + cls.getAttribute("sourcefilename");
                var changed = changes.getOrDefault(path, Set.of());
                for (Element method : children(cls, "method")) {
                    countMethod(method, path, changed, ranges, methods);
                }
            }
        }
        summary.append("| Metric | Covered | Total | Coverage | Target | Result |\n")
                .append("| --- | ---: | ---: | ---: | ---: | --- |\n")
                .append(lines.row("LINE", 85))
                .append(branches.row("BRANCH", 75))
                .append(methods.row("METHOD", 85))
                .append(instructions.row("INSTRUCTION", 85));
        if (baseClasses == null) {
            summary.append("| CLASS | N/A | N/A | N/A | 100% | Base classes unavailable |\n");
        } else {
            summary.append(classes.row("CLASS", 100));
        }
        if (!uncoveredClasses.isEmpty()) {
            summary.append("\nNew classes without coverage:\n\n");
            uncoveredClasses.stream().sorted().forEach(name -> summary.append("- `").append(name).append("`\n"));
        }
    }

    private static Set<String> baseClasses() throws Exception {
        String directory = System.getenv("PR_BASE_CLASSES");
        if (directory == null || !Files.isDirectory(Path.of(directory))) {
            return null;
        }
        Path root = Path.of(directory);
        Set<String> names = new HashSet<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".class"))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .map(path -> path.substring(0, path.length() - ".class".length()))
                    .forEach(names::add);
        }
        return names;
    }

    private static void countNewClass(Element cls, Counter classes, List<String> uncoveredClasses) {
        for (Element counter : children(cls, "counter")) {
            if ("CLASS".equals(counter.getAttribute("type"))) {
                int covered = number(counter, "covered");
                int missed = number(counter, "missed");
                classes.add(covered, covered + missed);
                if (missed > 0) {
                    uncoveredClasses.add(cls.getAttribute("name").replace('/', '.'));
                }
                return;
            }
        }
    }

    private static void countMethod(Element method, String path, Set<Integer> changed,
                                    Map<MethodKey, List<Span>> ranges, Counter methods) {
        if (changed.isEmpty() || !method.hasAttribute("line")) {
            return;
        }
        var key = new MethodKey(path, method.getAttribute("name"));
        int entry = number(method, "line");
        var span = ranges.getOrDefault(key, List.of()).stream()
                .filter(candidate -> candidate.contains(entry))
                .min(Comparator.comparingLong(candidate -> candidate.end() - candidate.start()));
        if (span.isEmpty() || changed.stream().noneMatch(line -> span.get().contains(line))) {
            return;
        }
        for (Element counter : children(method, "counter")) {
            if ("METHOD".equals(counter.getAttribute("type"))) {
                int covered = number(counter, "covered");
                methods.add(covered, covered + number(counter, "missed"));
                return;
            }
        }
    }

    private static List<Element> children(Element parent, String name) {
        var children = new ArrayList<Element>();
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(element.getTagName())) {
                children.add(element);
            }
        }
        return children;
    }

    private static int number(Element element, String attribute) {
        return Integer.parseInt(element.getAttribute(attribute));
    }

    private static Map<String, Set<Integer>> changedLines(String base) throws Exception {
        if (base == null || !base.matches("[0-9a-fA-F]{40,64}")) {
            throw new IllegalArgumentException("PR_BASE_SHA must be a Git commit SHA");
        }
        var git = new ProcessBuilder("git", "-c", "core.quotePath=false", "diff", "--no-ext-diff",
                "--no-color", "--find-renames", "--unified=0", "--dst-prefix=b/", base + "...HEAD", "--")
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String diff = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (git.waitFor() != 0) {
            throw new IllegalStateException("git diff failed");
        }
        var files = new HashMap<String, Set<Integer>>();
        String path = null;
        boolean inHeader = false;
        for (String line : diff.lines().toList()) {
            var hunk = HUNK.matcher(line);
            if (line.startsWith("diff --git ")) {
                path = null;
                inHeader = true;
            } else if (inHeader && line.startsWith("+++ b/")) {
                path = line.substring(6).split("\t", 2)[0];
                inHeader = false;
            } else if (path != null && hunk.find()) {
                int start = Integer.parseInt(hunk.group(1));
                int count = hunk.group(2) == null ? 1 : Integer.parseInt(hunk.group(2));
                var changed = files.computeIfAbsent(path, ignored -> new HashSet<>());
                for (int number = start; number < start + count; number++) {
                    changed.add(number);
                }
            }
        }
        return files;
    }

    private static Map<MethodKey, List<Span>> methodRanges(Map<String, Set<Integer>> changes) throws Exception {
        var paths = changes.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .filter(path -> path.startsWith(SOURCE_ROOT) && path.endsWith(".java")).toList();
        var ranges = new HashMap<MethodKey, List<Span>>();
        if (paths.isEmpty()) {
            return ranges;
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, files, null, List.of("-proc:none"), null,
                    files.getJavaFileObjectsFromStrings(paths));
            var positions = Trees.instance(task).getSourcePositions();
            for (var unit : task.parse()) {
                String path = Path.of("").toAbsolutePath()
                        .relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\', '/');
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitMethod(MethodTree method, Void unused) {
                        long start = positions.getStartPosition(unit, method);
                        long end = positions.getEndPosition(unit, method);
                        if (method.getBody() != null && start >= 0 && end > start) {
                            var key = new MethodKey(path, method.getName().toString());
                            var span = new Span(unit.getLineMap().getLineNumber(start),
                                    unit.getLineMap().getLineNumber(end - 1));
                            ranges.computeIfAbsent(key, ignored -> new ArrayList<>()).add(span);
                        }
                        return super.visitMethod(method, unused);
                    }
                }.scan(unit, null);
            }
        }
        return ranges;
    }
}
