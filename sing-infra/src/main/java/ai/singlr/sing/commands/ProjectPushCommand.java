/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.GitHubPusher;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.SingPaths;
import ai.singlr.sing.engine.WorkspaceFiles;
import ai.singlr.sing.gen.SingYamlGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
    name = "push",
    description = "Push a project descriptor to the shared project repository.",
    mixinStandardHelpOptions = true)
public final class ProjectPushCommand implements Runnable {

  private static final String DEFAULT_REPO = "";
  private static final String DEFAULT_REF = "main";

  @Parameters(
      index = "0",
      description = "Project name (used as the folder name in the projects repository).")
  private String name;

  @Option(
      names = {"-f", "--file"},
      description = "Path to sing.yaml to push.",
      defaultValue = "sing.yaml")
  private String file;

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
      description = "Branch in the projects repository.",
      defaultValue = DEFAULT_REF)
  private String ref;

  @Option(names = "--message", description = "Commit message.")
  private String message;

  @Option(names = "--json", description = "Output in JSON format.")
  private boolean json;

  @Option(names = "--dry-run", description = "Show what would be pushed without writing.")
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

    var singYamlPath = SingPaths.resolveSingYaml(name, file);
    if (!Files.exists(singYamlPath)) {
      throw new IllegalStateException(
          "Project descriptor not found: "
              + singYamlPath.toAbsolutePath()
              + "\n  Create a sing.yaml in the current directory, or specify one with --file.");
    }

    var config = SingYaml.fromMap(YamlUtil.parseMap(Files.readString(singYamlPath)));
    var content = SingYamlGenerator.generate(templatize(config));

    var token = resolveToken();
    var remotePath = name + "/sing.yaml";
    var commitMessage = Objects.requireNonNullElse(message, "Update " + remotePath);

    if (!json) {
      Banner.printBranding(System.out, Ansi.AUTO);
      System.out.println();
    }

    if (dryRun) {
      if (json) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", name);
        map.put("repo", repo);
        map.put("ref", ref);
        map.put("file", singYamlPath.toAbsolutePath().toString());
        map.put("remote_path", remotePath);
        map.put("message", commitMessage);
        map.put("dry_run", true);
        System.out.println(YamlUtil.dumpJson(map));
      } else {
        System.out.println(
            Ansi.AUTO.string(
                "  @|faint [dry-run] Would push "
                    + singYamlPath.toAbsolutePath()
                    + " to "
                    + repo
                    + "/"
                    + remotePath
                    + " (branch: "
                    + ref
                    + ")|@"));
      }
      return;
    }

    if (!json) {
      System.out.println(
          Ansi.AUTO.string(
              "  @|bold Pushing|@ " + singYamlPath + " @|bold to|@ " + repo + "/" + remotePath));
    }

    var result = GitHubPusher.pushFile(repo, remotePath, content, commitMessage, token, ref);

    var filesPushed = pushFilesDirectory(singYamlPath, token, commitMessage);

    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("repo", repo);
      map.put("ref", ref);
      map.put("remote_path", remotePath);
      map.put("commit_sha", result.commitSha());
      map.put("commit_url", result.commitUrl());
      map.put("created", result.created());
      map.put("files_pushed", filesPushed);
      System.out.println(YamlUtil.dumpJson(map));
      return;
    }

    System.out.println();
    if (result.created()) {
      System.out.println(
          Ansi.AUTO.string("  @|bold,green \u2713 Created|@ " + remotePath + " in " + repo));
    } else {
      System.out.println(
          Ansi.AUTO.string("  @|bold,green \u2713 Updated|@ " + remotePath + " in " + repo));
    }
    if (filesPushed > 0) {
      System.out.println(
          Ansi.AUTO.string("  @|bold,green \u2713 Pushed|@ " + filesPushed + " workspace file(s)"));
    }
    if (!result.commitUrl().isEmpty()) {
      System.out.println(Ansi.AUTO.string("  @|faint " + result.commitUrl() + "|@"));
    }
  }

  private int pushFilesDirectory(Path singYamlPath, String token, String commitMsg)
      throws Exception {
    var filesDir = WorkspaceFiles.resolveFilesDir(singYamlPath);
    if (filesDir == null) {
      return 0;
    }
    var entries = WorkspaceFiles.listFiles(filesDir);
    if (entries.isEmpty()) {
      return 0;
    }
    for (var entry : entries) {
      var remoteFilePath = name + "/files/" + entry.relativePath();
      var fileContent = Files.readString(entry.hostPath());
      if (!json) {
        System.out.println(
            Ansi.AUTO.string("  @|bold Pushing|@ files/" + entry.relativePath() + "..."));
      }
      GitHubPusher.pushFile(repo, remoteFilePath, fileContent, commitMsg, token, ref);
    }
    return entries.size();
  }

  static SingYaml templatize(SingYaml config) {
    var git = config.git();
    SingYaml.Git templateGit = null;
    if (git != null) {
      templateGit = new SingYaml.Git("${GIT_NAME}", "${GIT_EMAIL}", git.auth(), null);
    }
    var ssh = config.ssh();
    SingYaml.Ssh templateSsh = null;
    if (ssh != null) {
      templateSsh = new SingYaml.Ssh(ssh.user(), List.of("${SSH_PUBLIC_KEY}"));
    }
    return new SingYaml(
        config.name(),
        config.description(),
        config.resources(),
        config.image(),
        config.packages(),
        config.runtimes(),
        templateGit,
        config.repos(),
        config.services(),
        config.processes(),
        config.agent(),
        config.agentContext(),
        templateSsh);
  }

  private String resolveToken() {
    if (githubToken != null && !githubToken.isBlank()) {
      return githubToken;
    }
    var envToken = System.getenv("GITHUB_TOKEN");
    if (envToken != null && !envToken.isBlank()) {
      return envToken;
    }
    var prompted =
        ConsoleHelper.readPassword("  GitHub personal access token (needs 'repo' scope): ");
    if (prompted == null || prompted.isBlank()) {
      throw new IllegalArgumentException(
          "GitHub token required. Pass --github-token, set GITHUB_TOKEN, or enter interactively.");
    }
    return prompted;
  }
}
