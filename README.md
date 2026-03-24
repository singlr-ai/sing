# sing

A single native binary that provisions bare-metal servers and manages isolated dev environments for AI-assisted engineering. One binary, zero dependencies, fully declarative.

Built with Java 25 + picocli + GraalVM native-image. <1ms startup.

## Why

You have a bare-metal server. You work on multiple projects simultaneously, each needing its own JDK, Postgres, Meilisearch, Redpanda, and AI coding agents — fully isolated so a runaway agent in one project can't affect another.

`sing` provisions the server, creates project environments as Incus system containers, manages their lifecycle, and orchestrates AI agents inside them with spec-driven workflows, guardrails, and rollback safety. Each project gets a complete Ubuntu 24.04 userspace with its own filesystem, network stack, and rootless Podman runtime.

## Day 0: Server + Project Setup

```bash
# Install sing (single binary, no dependencies)
curl -fsSL https://raw.githubusercontent.com/singlr-ai/sing/main/install.sh | bash

# Initialize server (one-time, needs sudo)
sudo sing host init
```

`host init` installs Incus, creates a storage pool (dir by default, ZFS with `--storage zfs --disk /dev/sdX`), configures networking, and caches the base Ubuntu 24.04 image.

```bash
# Pull project descriptor from shared repo (team projects)
export GITHUB_TOKEN=ghp_...
sing project pull acme-health

# Or generate a new sing.yaml interactively
sing project init

# Create the environment
sing project create acme-health
```

`project create` provisions an Incus container with everything declared in `sing.yaml` — installs runtimes, starts Podman services, clones repos, configures git identity, generates agent context files, and sets up the harness.

```bash
# Print SSH config for your editor
sing connect acme-health
```

Add the output to `~/.ssh/config`, then connect in Zed: `Cmd+Shift+P` → "Connect to SSH Host" → `acme-health`.

## Day 2: The Two Modes

Engineers work in two modes. `sing` supports both from the same project.

### Interactive Mode (Daytime)

Open Zed, connect via SSH remote dev, start the agent from Zed's Agent Panel. You're in the loop — brainstorming, exploring code, writing specs, reviewing output. `sing` is invisible here; the generated context files (CLAUDE.md, SECURITY.md, `.context/`) guide the agent.

```bash
sing switch acme-health     # start container, show connection info
```

Developer processes (`java -jar`, `npm run dev`) run interactively in Zed terminal tabs. Infrastructure services (Postgres, Meilisearch, etc.) are managed by Podman with `--restart=always` and survive container reboots automatically.

### Autonomous Mode (Overnight)

Write specs during the day. Walk away. The agent works through them overnight.

```bash
sing dispatch acme-health   # pick next ready spec, launch agent
```

`dispatch` reads `specs/index.yaml` from the container, finds the next pending spec (respecting dependencies and assignee), reads the detailed `spec.md`, and launches the agent with full context. Guardrails enforce time limits. Auto-snapshot provides rollback safety.

## Spec-Driven Workflow

Specs are the unit of work. Each spec lives in its own directory inside `specs/`, checked into a shared repo so the team can see and assign work.

### Structure

```
specs/
├── index.yaml                # Ordered list: id, title, status, assignee, depends_on
├── oauth-flow/
│   ├── spec.md               # What to build and why (brainstormed with agent in Zed)
│   └── plan.md               # How to build it (optional, for complex specs)
├── payment-integration/
│   ├── spec.md
│   └── plan.md
└── fix-footer-typo/
    └── spec.md               # Simple spec, no plan needed
```

### index.yaml

```yaml
specs:
  - id: oauth-flow
    title: "Implement OAuth flow with Google"
    status: pending
    assignee: alice
    depends_on: []

  - id: payment-integration
    title: "Stripe payment webhook"
    status: pending
    assignee: bob
    depends_on:
      - oauth-flow

  - id: fix-footer-typo
    title: "Fix typo in footer"
    status: done
```

### Lifecycle

```
pending → in_progress → review → done → archive
```

**pending**: Ready for an agent to pick up. **in_progress**: Agent is working. **review**: Work complete, security review and code review hooks run automatically. **done**: All reviews pass. **archive**: Cleaned up (`sing task archive`).

### The Full Loop

```
Morning (Zed, interactive):
  Engineer + agent brainstorm → write specs/oauth-flow/spec.md
  Optionally plan → write specs/oauth-flow/plan.md
  Push specs to shared repo

Evening:
  sing dispatch acme-health

Overnight:
  Agent reads spec.md, works, commits, pushes branch
  Reviews run automatically at spec completion
  Guardrails enforce time limits

Next morning:
  sing agent status              # all projects at a glance
  sing agent report acme-health  # detailed: commits, spec progress, review results
  Review PRs, merge or address findings
```

### Team Coordination

Specs live in a shared private repo (e.g., `singlr-ai/projects/acme-health/specs/`). Multiple engineers, each with their own container:

- Alice specs out OAuth, assigns to herself
- Bob specs out payments, depends on Alice's OAuth work
- `sing dispatch` respects `assignee` — each engineer's agent only picks up their specs (or unassigned ones)
- Dependencies prevent premature work — payments won't start until OAuth is done

No project board needed. The spec directory *is* the board. Git history is the audit trail.

## Context Generation

`sing run` (or `sing agent context regen`) generates a complete agent environment from `sing.yaml`. Context files are agent-agnostic — Claude Code gets `CLAUDE.md`, Codex gets `AGENTS.md`, Gemini gets `GEMINI.md`. Same content, different format.

| Generated | Purpose |
|-----------|---------|
| `CLAUDE.md` / `AGENTS.md` / `GEMINI.md` | Tech stack, conventions, runtimes, services, autonomous work protocol |
| `SECURITY.md` | Zero-trust principles, OWASP Top 10, input validation, secrets management |
| `.context/` repository | Persistent knowledge across sessions (system overview, patterns, failure log) |
| Methodology skills | `/spec` (write spec first), `/verify` (run tests and block on failures) |
| Post-task hooks | Auto-trigger security audit and code review at spec completion |

The autonomous work protocol in the context file tells the agent how to read specs, update status, and hand off — this works identically across Claude Code, Codex, and Gemini.

### `.context/` — Institutional Memory

```
.context/
  system/       # always loaded — project overview, key decisions
  patterns/     # discovered architectural patterns and conventions
  failures/     # what went wrong and how it was fixed
```

Agents read `system/README.md` at session start and update these files as they learn. Committed alongside code so knowledge persists across sessions and engineers.

## Methodology

```yaml
agent:
  methodology:
    approach: spec-driven    # spec-driven | tdd | free-form
    verify: "mvn clean test"
    lint: "mvn spotless:check"
```

- **`spec-driven`**: Agent writes a spec before coding. Generated as a `/spec` skill.
- **`tdd`**: Agent writes failing tests first, then implements until green.
- **`free-form`**: No methodology constraints.
- **`verify`** / **`lint`**: Commands injected as skills the agent runs after implementation.

## Guardrails

```yaml
agent:
  guardrails:
    max_duration: 4h
    action: snapshot-and-stop
```

When the time limit triggers, the agent is stopped and rolled back to the pre-launch snapshot. The watcher starts automatically with `sing dispatch` and `sing run --background`.

Actions: `snapshot-and-stop` (rollback), `stop` (keep changes), `notify` (webhook, agent continues).

## Agent Commands

```bash
# Dispatch
sing dispatch acme-health              # next ready spec, background launch
sing dispatch acme-health --spec auth  # specific spec by ID

# Run (context regen + launch)
sing run acme-health                   # interactive mode (Zed)
sing run acme-health --task "..."      # headless with explicit task
sing run acme-health --background      # background, picks next spec

# Monitor
sing agent status                      # all projects at a glance
sing agent status acme-health          # single project detail
sing agent log acme-health -f          # stream output live
sing agent report acme-health          # morning-after summary

# Control
sing agent stop acme-health            # SIGTERM → SIGKILL after 3s
sing agent watch acme-health           # enforce guardrails (auto-started by dispatch)

# Quality
sing agent audit acme-health           # cross-agent security audit
sing agent review acme-health          # cross-agent code review
sing agent sweep acme-health           # codebase cleanup pass
```

## Multi-Agent Support

`sing` is agent-agnostic. Configure the primary agent and optionally install others for cross-agent review:

```yaml
agent:
  type: claude-code
  install:
    - claude-code
    - gemini
  security_audit:
    enabled: true
    # auditor: gemini     # defaults to a different agent than primary
  code_review:
    enabled: true
```

Claude codes. Gemini reviews. Or the same agent does both with fresh context. The hooks fire at spec completion, not session stop — so reviews always see complete, coherent work.

## Example `sing.yaml`

```yaml
name: acme-health
description: "Acme Health Platform"

resources:
  cpu: 4
  memory: 12GB
  disk: 150GB

image: ubuntu/24.04

runtimes:
  jdk: 25
  node: 22
  maven: "3.9.9"

git:
  name: "Acme Engineering"
  email: "eng@acme.com"
  auth: token

repos:
  - url: "https://github.com/acme/backend.git"
    path: "backend"
    branch: "main"
  - url: "https://github.com/acme/webapp.git"
    path: "webapp"

services:
  postgres:
    image: postgres:16
    ports: [5432]
    environment:
      POSTGRES_DB: acme
      POSTGRES_USER: dev
      POSTGRES_PASSWORD: dev
    volumes:
      - pgdata:/var/lib/postgresql/data

  meilisearch:
    image: getmeili/meilisearch:latest
    ports: [7700]
    environment:
      MEILI_ENV: development

agent:
  type: claude-code
  auto_branch: true
  auto_snapshot: true
  specs_dir: specs
  methodology:
    approach: spec-driven
    verify: "mvn clean test"
    lint: "mvn spotless:check"
  guardrails:
    max_duration: 4h
    action: snapshot-and-stop

ssh:
  user: dev
  authorized_keys:
    - "ssh-ed25519 AAAA... you@laptop"
```

## Project Lifecycle

```bash
sing project create acme-health   # provision container from sing.yaml
sing up acme-health               # start stopped container
sing down acme-health             # stop container (preserves state)
sing switch acme-health           # start + show connection info
sing ps                           # list all projects with status
sing snap acme-health             # create snapshot
sing restore acme-health snap-01  # rollback to snapshot

# Modify running projects
sing project add service acme-health
sing project add repo acme-health
sing project destroy acme-health  # delete container and state
```

## Building from Source

Requires JDK 25+ and Maven 3.9+.

```bash
mvn clean test                    # run tests (588 tests)
mvn clean package                 # build JAR

# Native image (requires GraalVM JDK 25)
JAVA_HOME=/usr/lib/jvm/graalvm-jdk-25 mvn clean package -Pnative -DskipTests
```

Every command supports `--help`. State-modifying commands support `--dry-run`. All commands support `--json` for machine-parseable output.

## License

[MIT](LICENSE)
