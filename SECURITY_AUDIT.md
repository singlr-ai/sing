# Sail Security Audit

## Scope
This audit covers the current `sail` CLI and runtime trust boundaries:

- Engineer workstation invoking `sail` locally or through SSH.
- Bare-metal Ubuntu host running Incus and project containers.
- Project containers running developer tooling and agent CLIs as the `dev` user.
- Local API server used by Chorus through an SSH tunnel.
- Spec directories, generated project files, agent session files, logs, webhooks, GitHub tokens, and generated handoff/report artifacts.

## Threat Model

### Assets
- Host root and Incus administration access.
- Project container filesystems, repos, specs, snapshots, and agent session state.
- SSH keys, GitHub tokens, webhook URLs, API bearer tokens, and generated logs/reports.
- The engineer's ability to approve and dispatch autonomous agents safely.

### Trust Boundaries
- CLI arguments and YAML configuration cross from the engineer workstation into host and container command execution.
- Spec content crosses from project data into agent prompts and dispatch flows.
- Local API requests cross from Chorus into `sail` host operations through bearer-token authentication.
- Agent output crosses from untrusted model/tool execution into PR, handoff, report, and review workflows.

### Primary Risks
- Command injection through interpolated paths, branches, spec IDs, repo paths, or generated task content.
- Exposing the local API beyond loopback without intentional operator opt-in.
- Token disclosure through permissive token-file permissions, symlinks, logs, errors, or command output.
- Cross-project access through path traversal, invalid container names, or unsafe generated SSH configuration.
- Prompt injection in specs or generated handoff content causing unsafe automation or misleading reviewer agents.
- Supply-chain drift in Maven dependencies, GitHub Actions, installer scripts, and agent CLI bootstrap commands.

## Findings Fixed

### API bind address requires explicit remote opt-in
`sail api` now refuses to bind to non-loopback addresses unless `--allow-remote` is supplied. This keeps the default local API posture local-first even when an operator mistypes `--host 0.0.0.0`.

### API token file hardening
The API token store now rejects non-regular token paths, reapplies owner-only permissions before reusing existing tokens, and creates new token files with restrictive POSIX permissions when the filesystem supports them.

### Duplicate bearer headers rejected
The API authenticator now rejects requests with multiple `Authorization` headers instead of relying on the first header value.

### Agent launch shell interpolation reduced
Background and foreground agent launch commands now pass work directories and generated agent commands as positional arguments to `bash -c` rather than interpolating the working directory into the shell script.

### Spec write shell interpolation reduced
Spec index and markdown writes now pass output paths as positional arguments instead of concatenating target paths into shell redirection scripts.

### Dependency vulnerability scanning added
CI now runs OWASP Dependency-Check with the repository `NVD_API_KEY` secret, fails builds for CVSS 7+ findings, skips test-scope dependencies, installs reactor modules before the aggregate scan, caches NVD data, and uploads HTML reports.

## Accepted Risks

### Agent install commands remain enum-controlled shell snippets
Agent CLI install commands still run through shell because npm-based global installs are shell-oriented. The command strings are enum-controlled, not user-provided. If agent installation becomes user-extensible, move to typed installers before accepting arbitrary commands.

### Spec content remains untrusted prompt input
Spec markdown is intentionally passed to coding agents. The security boundary is operational: agent dispatch requires explicit user action, tool permissions still apply in Chorus, and reviewer/handoff workflows must treat model output as untrusted.

## Verification
- Focused regression suite: `mvn -pl sail-infra,sail-harness -am -Dtest=ApiTokenStoreTest,ApiRouterTest,ApiCommandTest,SpecWorkspaceTest,AgentSessionTest -Dsurefire.failIfNoSpecifiedTests=false test`
- Full verification: `mvn clean verify`
- CI dependency scan: `mvn install org.owasp:dependency-check-maven:12.1.8:aggregate -DskipTests -Djacoco.skip=true -Dformat=HTML -DfailBuildOnCVSS=7 -DskipTestScope=true -DnvdApiKey=${{ secrets.NVD_API_KEY }} -DdataDirectory=${{ runner.temp }}/dependency-check-data`
- Native packaging attempted with `mvn package -Pnative -DskipTests`; local execution requires GraalVM and this container uses Temurin, so CI remains the authoritative native-image check.
