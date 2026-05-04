package ai.singlr.sing.engine;

import ai.singlr.sing.config.Spec;
import ai.singlr.sing.config.SpecDirectory;
import ai.singlr.sing.config.YamlUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

public final class SpecIndexMigrator {

  private final ShellExec shell;

  public SpecIndexMigrator(ShellExec shell) {
    this.shell = Objects.requireNonNull(shell);
  }

  public Result migrate(String project, String specsDir, boolean keepIndex)
      throws IOException, InterruptedException, TimeoutException {
    NameValidator.requireValidProjectName(project);
    requireSafeSpecsDir(specsDir);
    if (shell.isDryRun()) {
      return new Result(false, 0, 0, false, List.of("Dry run: spec index migration not executed."));
    }

    var indexPath = specsDir + "/index.yaml";
    var indexCheck = exec(project, "test", "-f", indexPath);
    if (!indexCheck.ok()) {
      return new Result(false, 0, 0, false, List.of());
    }

    var index = exec(project, "cat", indexPath);
    if (!index.ok()) {
      throw new IOException("Failed to read legacy spec index: " + index.stderr());
    }

    var specs = parseSpecs(index.stdout());
    var converted = 0;
    var skipped = 0;
    var warnings = new ArrayList<String>();

    for (var spec : specs) {
      var specDir = specsDir + "/" + spec.id();
      var metadataPath = specDir + "/spec.yaml";
      var existing = exec(project, "test", "-f", metadataPath);
      if (existing.ok()) {
        var existingSpec = readExistingSpec(project, metadataPath);
        if (sameMetadata(existingSpec, spec)) {
          skipped++;
          continue;
        }
        skipped++;
        warnings.add(
            "Skipped " + spec.id() + ": spec.yaml already exists with different metadata.");
        continue;
      }
      var mkdir = exec(project, "mkdir", "-p", specDir);
      if (!mkdir.ok()) {
        throw new IOException(
            "Failed to create spec directory '" + spec.id() + "': " + mkdir.stderr());
      }
      writeMetadata(project, metadataPath, spec);
      converted++;
    }

    var removed = false;
    if (!keepIndex && warnings.isEmpty()) {
      var remove = exec(project, "rm", "-f", indexPath);
      if (!remove.ok()) {
        throw new IOException("Failed to remove legacy spec index: " + remove.stderr());
      }
      removed = true;
    }

    return new Result(true, converted, skipped, removed, List.copyOf(warnings));
  }

  private static List<Spec> parseSpecs(String yaml) {
    var map = YamlUtil.parseMap(yaml);
    var rawSpecs = map.get("specs");
    if (!(rawSpecs instanceof List<?> specs)) {
      return List.of();
    }
    var parsed = new ArrayList<Spec>();
    for (var rawSpec : specs) {
      if (!(rawSpec instanceof Map<?, ?> rawMap)) {
        throw new IllegalArgumentException("Each legacy spec index entry must be a map.");
      }
      parsed.add(Spec.fromMap(copyStringMap(rawMap)));
    }
    return List.copyOf(parsed);
  }

  private Spec readExistingSpec(String project, String metadataPath)
      throws IOException, InterruptedException, TimeoutException {
    var existing = exec(project, "cat", metadataPath);
    if (!existing.ok()) {
      throw new IOException("Failed to read existing spec metadata: " + existing.stderr());
    }
    return SpecDirectory.parseMetadata(YamlUtil.parseMap(existing.stdout()));
  }

  private void writeMetadata(String project, String metadataPath, Spec spec)
      throws IOException, InterruptedException, TimeoutException {
    var yaml = YamlUtil.dumpToString(SpecDirectory.generateMetadata(spec));
    var write =
        shell.exec(
            ContainerExec.asDevUser(
                project,
                List.of("bash", "-c", "printf '%s' \"$1\" > \"$2\"", "bash", yaml, metadataPath)));
    if (!write.ok()) {
      throw new IOException("Failed to write spec metadata: " + write.stderr());
    }
  }

  private ShellExec.Result exec(String project, String... args)
      throws IOException, InterruptedException, TimeoutException {
    return shell.exec(ContainerExec.asDevUser(project, List.of(args)));
  }

  private static void requireSafeSpecsDir(String specsDir) {
    if (specsDir == null
        || specsDir.isBlank()
        || specsDir.contains("..")
        || specsDir.chars().anyMatch(character -> character < 0x20 || character == 0x7f)) {
      throw new IllegalArgumentException("Invalid specsDir: " + specsDir);
    }
  }

  private static boolean sameMetadata(Spec left, Spec right) {
    return Objects.equals(normalized(left), normalized(right));
  }

  private static Map<String, Object> normalized(Spec spec) {
    return SpecDirectory.generateMetadata(spec);
  }

  private static Map<String, Object> copyStringMap(Map<?, ?> rawMap) {
    var map = new LinkedHashMap<String, Object>();
    for (var entry : rawMap.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new IllegalArgumentException("Spec metadata keys must be strings.");
      }
      map.put(key, entry.getValue());
    }
    return map;
  }

  public record Result(
      boolean indexFound, int converted, int skipped, boolean indexRemoved, List<String> warnings) {
    public Result {
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }
}
