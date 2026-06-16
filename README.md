# sail

A single native binary that provisions bare-metal servers and manages isolated dev environments for AI-assisted engineering. One binary, zero dependencies, fully declarative.

Built with Java 25 + picocli + GraalVM native-image. <1ms startup.

## Why

You have a bare-metal server. You work on multiple projects simultaneously, each needing its own JDK, Postgres, Meilisearch, Redpanda, and AI coding agents — fully isolated so a runaway agent in one project can't affect another.

`sail` provisions the server, creates project environments as Incus system containers, manages their lifecycle, and orchestrates AI agents inside them with spec-driven workflows, guardrails, and rollback safety. Each project gets a complete Ubuntu 24.04 userspace with its own filesystem, network stack, and rootless Podman runtime.

## Install

```bash
curl -fsSL https://raw.githubusercontent.com/singlr-ai/sing/main/install.sh | bash
```

A single static binary. `sail upgrade` updates it in place (and runs `sail migrate` to converge the database and provisioned host).

## The model: one main, many nodes

An organization runs **one main devbox** — the source of truth for the team's specs and project definitions, all held in a SQLite control-plane database. Every engineer has **their own box** (bare-metal Ubuntu running `sail`). A box pointed at a main is a **node**: it syncs specs and projects down from main and pushes its own work back.

There is no GitHub in this loop and no project board. The database **is** the board; `sail sync` is how it travels — over the locked-down `sail` SSH gateway, pure public-key auth, with conflict-parking so no one's work is ever clobbered.

### Stand up a main

```bash
sudo sail host init             # install Incus, storage pool, network, base image
sudo sail host ssh-identity     # provision the locked `sail` gateway user + shared data dir
sudo sail host sync --as-main   # declare this box the org's source of truth
```

Authorize an engineer (they send you the public key `sail join` printed on their box):

```bash
sail fde add mady --role member --key "ssh-ed25519 AAAA… sail-sync@madybox"
```

`--role` is `admin`, `member`, or `viewer` (read-only). Registering the key also refreshes the gateway's `authorized_keys`.

### Join as a node

```bash
sudo sail host init             # one-time control plane (a node needs no ssh-identity)
sail join <main-ip>             # generate this box's sync key, point it at main,
                                # and print the exact `sail fde add …` line for the operator
# …operator runs that line on main…
sail sync                       # reconcile: pull specs + projects + shared files from main
```

`sail join` never assumes `root` — it prompts for the handle main should authorize (defaulting to your own user, never `root`). Only a *public* key ever leaves your box.

## Projects

A project is declared by a `sail.yaml` (runtimes, services, repos, agent config). The **database is the source of truth** for that definition; the on-disk `~/.sail/projects/<name>/sail.yaml` is a materialized view.

```bash
sail project init                 # interactively author a new sail.yaml
sail project create acme-health   # provision the Incus container from the definition
sail project config acme-health   # show the definition + live container status
sail project edit acme-health     # change the definition (saves to the catalog; syncs out)
sail project connect acme-health  # print SSH config for your editor
```

`project create` provisions an Incus container with everything declared in `sail.yaml` — installs runtimes, starts Podman services with `--restart=always`, clones repos, configures git identity, generates agent context files, and pushes the project's `files/` bundle into `~/workspace/`.

**Getting a teammate's project:** `sail sync` brings its definition *and* its shared `files/` bundle onto your box; then `sudo sail project create <name>` spins it up locally (pass `--git-token` for private repos). Editing is database-first: `sail project edit` changes the catalog and replicates to every box on the next sync — hand-editing the materialized file is not the path.

```bash
# zero-config demo (Outline wiki — Postgres + Redis), bundled in the binary
sudo sail project demo
```

## Specs: the unit of work

Specs live in the control-plane database, not in git. Engineers manage them with `sail spec`; agents inside a container use the in-container `spec` CLI over a bind-mounted socket — one source of truth, no sync glue.

```bash
sail spec create acme-health --title "Stripe payment webhook"
sail spec board acme-health        # the team board: who's on what, what's ready
sail spec list acme-health
sail spec show acme-health auth
sail spec edit acme-health auth --assignee mady --depends-on oauth
sail spec history acme-health auth # full revision history; restore any version
```

A spec carries `title`, `status`, `assignee`, `depends_on`, `repos`, `agent`, `model`, `reasoning_effort`, `branch`. `repos` routes work to the right repository in a multi-repo project; `agent` routes to a specific installed agent (`claude-code` or `codex`), defaulting to `agent.type` in `sail.yaml`.

**Lifecycle:** `pending → in_progress → review → done` (plus `draft` and `archived`). Dispatch moves a spec to `in_progress`; the lifecycle reactor moves it to `review` when the agent session ends. Dependencies prevent premature work — a spec won't start until everything in `depends_on` is `done`.

**Team coordination:** Mady specs out payments depending on Uday's OAuth work; she `sail sync`s and her board shows both. `sail spec dispatch` respects `assignee`, so each engineer's agent picks up only their specs (or unassigned ones). Disjoint edits auto-merge on sync; a true same-field clash parks as a conflict for `sail conflicts` — the local copy is never overwritten.

## Day 2: the two modes

### Interactive (daytime)

Connect your editor over SSH remote dev, drive the agent from its panel. You're in the loop — brainstorming, writing specs, reviewing. `sail` is invisible; the generated context files (`CLAUDE.md`, `SECURITY.md`, `.context/`) guide the agent. Developer processes (`java -jar`, `npm run dev`) run in editor terminal tabs; infra services run under Podman `--restart=always` and survive reboots.

### Autonomous (overnight)

```bash
sail spec dispatch acme-health   # pick the next ready spec, launch the agent in the background
```

`dispatch` reads the next pending spec from the database (respecting dependencies and assignee), launches the agent with full context, starts the guardrail watcher, and auto-snapshots for rollback safety.

```bash
sail agent status                 # all projects at a glance
sail agent status acme-health     # single-project detail
sail agent log acme-health -f     # stream output live
sail agent report acme-health     # morning-after summary: commits, spec progress, reviews
sail agent stop acme-health       # SIGTERM → SIGKILL after a grace period
sail agent audit acme-health      # cross-agent security audit
sail agent review acme-health     # cross-agent code review
```

## Context generation

`sail agent run` (or `sail agent context regen`) generates an agent-agnostic environment from `sail.yaml` — Claude Code gets `CLAUDE.md`, Codex gets `AGENTS.md`. Files the engineer owns (a hand-written `CLAUDE.md`) are never overwritten.

| Generated | Purpose |
|-----------|---------|
| `CLAUDE.md` / `AGENTS.md` | Tech stack, conventions, runtimes, services, autonomous work protocol |
| `SECURITY.md` | Zero-trust principles, OWASP Top 10, input validation, secrets management |
| `.context/` | Persistent knowledge across sessions (system overview, patterns, failure log) |
| Methodology skills | `/spec` (write spec first), `/verify` (run tests, block on failures) |
| Post-task hooks | Security audit + code review at spec completion |

## Methodology & guardrails

```yaml
agent:
  type: claude-code
  auto_branch: true
  auto_snapshot: true
  methodology:
    approach: spec-driven        # spec-driven | tdd | free-form
    verify: "mvn clean test"
    lint: "mvn spotless:check"
  guardrails:
    max_duration: 4h
    action: snapshot-and-stop    # snapshot-and-stop | stop | notify
```

`verify`/`lint` are injected as skills the agent runs after implementing. When a guardrail trips, the watcher (auto-started by `dispatch` / `agent run --background`) stops the agent and, for `snapshot-and-stop`, rolls back to the pre-launch snapshot.

## Multi-agent

`sail` is agent-agnostic. Install more than one and use a different agent for review than for implementation:

```yaml
agent:
  type: claude-code
  install: [claude-code, codex]
  security_audit: { enabled: true }
  code_review:    { enabled: true }
```

Set `agent: codex` on a spec to override the project default for that unit of work. Hooks fire at spec completion — so reviews always see complete, coherent work.

## Example `sail.yaml`

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

agent:
  type: claude-code
  auto_branch: true
  auto_snapshot: true
  methodology:
    approach: spec-driven
    verify: "mvn clean test"
  guardrails:
    max_duration: 4h
    action: snapshot-and-stop

ssh:
  user: dev
  authorized_keys:
    - "ssh-ed25519 AAAA... you@laptop"
```

## Project lifecycle

```bash
sail project create acme-health                    # provision from the definition
sail project start acme-health                     # start a stopped container
sail project stop acme-health                      # stop (preserves state)
sail project restart acme-health                   # start + show connection info
sail project containers                            # list all projects with status
sail project snapshot create acme-health
sail project snapshot restore acme-health snap-01  # rollback to a snapshot

# modify a project (database-first; replicates on sync)
sail project edit acme-health                      # edit the whole definition
sail project service add acme-health               # add a service
sail project repo add acme-health                  # add a repo
sail project files ls --project acme-health        # shared, synced workspace files
sudo sail project resources set acme-health --memory 16GB
sail project destroy acme-health                   # delete container and state
```

## Upgrading

```bash
sail upgrade
```

Downloads the signed binary, verifies its checksum, installs it, runs `sail migrate` (which converges the database — seeding the demo, importing legacy descriptors, reconciling `authorized_keys`), and restarts `sail-api` if it was running. Single-box installs need no new commands; sync is opt-in.

## Building from source

Requires JDK 25+ and Maven 3.9+.

```bash
mvn clean verify                  # build + run the full test suite with coverage gates

# native image (requires GraalVM JDK 25)
JAVA_HOME=/usr/lib/jvm/graalvm-jdk-25 mvn clean package -Pnative -DskipTests
```

Every command supports `--help`. State-modifying commands support `--dry-run`. All commands support `--json` for machine-parseable output.

## License

[MIT](LICENSE)
