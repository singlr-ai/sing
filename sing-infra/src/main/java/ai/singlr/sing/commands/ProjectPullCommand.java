/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.PlaceholderResolver;
import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.config.YamlMerger;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.GitHubFetcher;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.SingPaths;
import ai.singlr.sing.engine.WorkspaceFiles;
import ai.singlr.sing.gen.SingYamlGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
    name = "pull",
    description = "Pull a project descriptor from the shared project repository.",
    mixinStandardHelpOptions = true)
public final class ProjectPullCommand implements Runnable {

  private static final String DEFAULT_REPO = "";
  private static final String DEFAULT_REF = "main";

  @Parameters(index = "0", description = "Project name (folder name in the projects repository).")
  private String name;

  @Option(
      names = "--github-token",
      description = "GitHub personal access token (falls back to GITHUB_TOKEN env var).")
  private String githubToken;

  @Option(
      names = "--repo",
      description = "Projects repository in owner/name format.",
      defaultValue = DEFAULT_REPO)
  private String repo;

  @Option(
      names = "--ref",
      description = "Branch or tag in the projects repository.",
      defaultValue = DEFAULT_REF)
  private String ref;

  @Option(
      names = {"-o", "--output"},
      description =
          "Output path for the resolved sing.yaml (default: ~/.sing/projects/<name>/sing.yaml).")
  private String output;

  @Option(names = "--json", description = "Output in JSON format.")
  private boolean json;

  @Option(names = "--dry-run", description = "Show what would be fetched without writing.")
  private boolean dryRun;

  @Spec private CommandSpec spec;

  @Override
  public void run() {
    try {
      execute();
    } catch (Exception e) {
      var msg = Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
      System.err.println(Banner.errorLine(msg, Ansi.AUTO));
      throw new picocli.CommandLine.ExecutionException(spec.commandLine(), msg, e);
    }
  }

  private void execute() throws Exception {
    NameValidator.requireValidProjectName(name);

    if (repo == null || repo.isBlank()) {
      throw new IllegalArgumentException(
          "--repo is required. Specify the GitHub repository that holds your project descriptors"
              + " (e.g. --repo your-org/projects).");
    }

    var token = resolveToken();

    if (!json) {
      Banner.printBranding(System.out, Ansi.AUTO);
      System.out.println();
    }

    if (!json) {
      System.out.println(Ansi.AUTO.string("  @|bold Fetching global.yaml...|@"));
    }
    var globalContent = GitHubFetcher.fetchRawFile(repo, "global.yaml", token, ref);
    var globalMap =
        globalContent != null ? YamlUtil.parseMap(globalContent) : Map.<String, Object>of();
    if (!json) {
      if (globalContent != null) {
        System.out.println(Ansi.AUTO.string("  @|green \u2713|@ global.yaml loaded"));
      } else {
        System.out.println(Ansi.AUTO.string("  @|faint \u2192 No global.yaml found (skipped)|@"));
      }
    }

    var projectPath = name + "/sing.yaml";
    if (!json) {
      System.out.println(Ansi.AUTO.string("  @|bold Fetching " + projectPath + "...|@"));
    }
    var projectContent = GitHubFetcher.fetchRawFile(repo, projectPath, token, ref);
    if (projectContent == null) {
      var hint =
          (token == null || token.isBlank())
              ? "\n  If the repository is private, provide a token with --github-token or GITHUB_TOKEN."
              : "\n  Check that the file exists and your token has 'repo' scope.";
      throw new IllegalStateException(
          "Project '"
              + name
              + "' not found in "
              + repo
              + " (ref: "
              + ref
              + ").\n"
              + "  Expected file: "
              + projectPath
              + hint);
    }
    if (!json) {
      System.out.println(Ansi.AUTO.string("  @|green \u2713|@ " + projectPath + " loaded"));
    }
    var projectMap = YamlUtil.parseMap(projectContent);

    var mergedMap = YamlMerger.deepMerge(globalMap, projectMap);

    var config = SingYaml.fromMap(mergedMap);
    var yamlContent = SingYamlGenerator.generate(config);
    var canonicalOutputPath = defaultOutputPath(name);

    if (dryRun) {
      var dryRunPath = output != null ? output : canonicalOutputPath.toAbsolutePath().toString();
      if (json) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", name);
        map.put("repo", repo);
        map.put("ref", ref);
        map.put("global_found", globalContent != null);
        map.put("output", dryRunPath);
        map.put("dry_run", true);
        System.out.println(YamlUtil.dumpJson(map));
      } else {
        System.out.println();
        System.out.println(
            Ansi.AUTO.string("  @|faint [dry-run] Would write to: " + dryRunPath + "|@"));
      }
      return;
    }

    if (!json) {
      System.out.println();
    }

    var outputPath = output != null ? Path.of(output) : canonicalOutputPath;
    var resolvedYaml = resolveWithExistingValues(yamlContent, outputPath);
    if (outputPath.getParent() != null) {
      Files.createDirectories(outputPath.getParent());
    }
    Files.writeString(outputPath, resolvedYaml);

    var outputDir = outputPath.getParent() != null ? outputPath.getParent() : Path.of(".");
    var filesPulled = pullFilesDirectory(token, name, outputDir);

    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("repo", repo);
      map.put("ref", ref);
      map.put("global_found", globalContent != null);
      map.put("output", outputPath.toAbsolutePath().toString());
      map.put("files_pulled", filesPulled);
      System.out.println(YamlUtil.dumpJson(map));
      return;
    }

    System.out.println();
    System.out.println(
        Ansi.AUTO.string(
            "  @|bold,green \u2713 Project descriptor saved:|@ " + outputPath.toAbsolutePath()));
    if (filesPulled > 0) {
      System.out.println(
          Ansi.AUTO.string(
              "  @|bold,green \u2713 Workspace files pulled:|@ " + filesPulled + " file(s)"));
    }
    System.out.println();
    var stateDir = SingPaths.projectDir(name);
    if (Files.exists(stateDir) && isCanonicalOutputPath(outputPath)) {
      System.out.println(Ansi.AUTO.string("  @|bold Next:|@ sing project apply " + name));
    } else if (Files.exists(stateDir)) {
      System.out.println(
          Ansi.AUTO.string(
              "  @|bold Next:|@ re-run without @|bold --output|@ to update "
                  + canonicalOutputPath.toAbsolutePath()
                  + ", then run @|bold sing project apply "
                  + name
                  + "|@"));
    } else if (isCanonicalOutputPath(outputPath)) {
      System.out.println(Ansi.AUTO.string("  @|bold Next:|@ sing project create " + name));
    } else {
      System.out.println(
          Ansi.AUTO.string("  @|bold Next:|@ sing project create " + name + " -f " + outputPath));
    }
  }

  private int pullFilesDirectory(String token, String projectName, Path outputDir)
      throws Exception {
    var filesPath = projectName + "/files";
    if (!json) {
      System.out.println(Ansi.AUTO.string("  @|bold Fetching workspace files...|@"));
    }
    var entries = GitHubFetcher.fetchDirectoryTree(repo, filesPath, token, ref);
    if (entries.isEmpty()) {
      if (!json) {
        System.out.println(
            Ansi.AUTO.string("  @|faint \u2192 No files/ directory found (skipped)|@"));
      }
      return 0;
    }
    var filesDir = outputDir.resolve("files");
    for (var entry : entries) {
      var localPath = filesDir.resolve(entry.relativePath());
      if (localPath.getParent() != null) {
        Files.createDirectories(localPath.getParent());
      }
      var content = GitHubFetcher.fetchRawFile(repo, entry.path(), token, ref);
      if (content != null) {
        Files.writeString(localPath, content);
        WorkspaceFiles.setExecutableIfNeeded(localPath);
        if (!json) {
          System.out.println(Ansi.AUTO.string("  @|green \u2713|@ files/" + entry.relativePath()));
        }
      }
    }
    return entries.size();
  }

  static Path defaultOutputPath(String name) {
    return SingPaths.projectDir(name).resolve("sing.yaml");
  }

  private boolean isCanonicalOutputPath(Path outputPath) {
    return outputPath
        .toAbsolutePath()
        .normalize()
        .equals(defaultOutputPath(name).toAbsolutePath().normalize());
  }

  private String resolveWithExistingValues(String yamlContent, Path outputPath) throws IOException {
    if (Files.exists(outputPath)) {
      var existingContent = Files.readString(outputPath);
      var existingConfig = SingYaml.fromMap(YamlUtil.parseMap(existingContent));
      var replacements = new java.util.LinkedHashMap<String, String>();
      if (existingConfig.git() != null) {
        if (existingConfig.git().name() != null) {
          replacements.put("${GIT_NAME}", existingConfig.git().name());
        }
        if (existingConfig.git().email() != null) {
          replacements.put("${GIT_EMAIL}", existingConfig.git().email());
        }
      }
      if (existingConfig.ssh() != null
          && existingConfig.ssh().authorizedKeys() != null
          && !existingConfig.ssh().authorizedKeys().isEmpty()) {
        replacements.put("${SSH_PUBLIC_KEY}", existingConfig.ssh().authorizedKeys().getFirst());
      }
      var resolved = yamlContent;
      for (var entry : replacements.entrySet()) {
        resolved = resolved.replace(entry.getKey(), entry.getValue());
      }
      if (!resolved.contains("${")) {
        if (!json) {
          System.out.println(
              Ansi.AUTO.string("  @|faint → Using existing values from " + outputPath + "|@"));
        }
        return resolved;
      }
    }
    return PlaceholderResolver.resolve(yamlContent);
  }

  private String resolveToken() {
    if (githubToken != null && !githubToken.isBlank()) {
      return githubToken;
    }
    var envToken = System.getenv("GITHUB_TOKEN");
    if (envToken != null && !envToken.isBlank()) {
      return envToken;
    }
    try {
      var prompted =
          ConsoleHelper.readPassword("  GitHub personal access token (needs 'repo' scope): ");
      if (prompted == null || prompted.isBlank()) {
        throw new IllegalArgumentException(
            "GitHub token required. Pass --github-token, set GITHUB_TOKEN, or enter interactively.");
      }
      return prompted;
    } catch (EchoDisabledUnavailableException e) {
      throw new IllegalArgumentException(
          "Unable to read GitHub token interactively in this terminal.\n\n"
              + "Provide the token via one of:\n"
              + "  --github-token <token>\n"
              + "  GITHUB_TOKEN environment variable\n\n"
              + "Then re-run: sing project pull <name>");
    }
  }
}
