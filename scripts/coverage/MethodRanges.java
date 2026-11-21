import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

class MethodRanges {
    public static void main(String[] paths) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(
                    null, files, null, List.of("-proc:none"), null,
                    files.getJavaFileObjectsFromStrings(Arrays.asList(paths)));
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            for (CompilationUnitTree unit : task.parse()) {
                String path = Path.of("").toAbsolutePath()
                        .relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\', '/');
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitMethod(MethodTree method, Void unused) {
                        long start = positions.getStartPosition(unit, method);
                        long end = positions.getEndPosition(unit, method);
                        if (method.getBody() != null && start >= 0 && end > start) {
                            System.out.printf("%s\t%s\t%d\t%d%n", path, method.getName(),
                                    unit.getLineMap().getLineNumber(start),
                                    unit.getLineMap().getLineNumber(end - 1));
                        }
                        return super.visitMethod(method, unused);
                    }
                }.scan(unit, null);
            }
        }
    }
}
