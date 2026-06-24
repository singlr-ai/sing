# sail

**Kubernetes for coding agents.** A single native binary that turns bare-metal servers
into isolated, spec-driven dev environments where AI agents do the work — with hard
isolation, rollback safety, cross-agent review, and team-wide coordination.

Sail is not a coding agent. It's everything *around* them: the environments they run in,
the specs they pick up, the reviews they pass, and the shared state a team works from.

Built with Java 25 + picocli + GraalVM native-image. One binary, zero runtime
dependencies, <1ms startup.

## Install

```bash
curl -fsSL https://raw.githubusercontent.com/standardapplied/sail/main/install.sh | bash
```

`sail upgrade` updates the binary in place and converges the database. Linux (amd64) runs
the full host; macOS (arm64) runs as a thin client that drives a remote host over SSH.

## The model: one main, many nodes

An org runs **one main box** — the source of truth for the team's specs and project
definitions, held in a SQLite control plane. Every engineer has **their own box**; a box
pointed at main is a **node** that syncs specs, project definitions, and shared files down
from main and pushes its own work back.

There's no GitHub in this loop and no separate board — the database *is* the board, and
`sail sync` is how it travels: over a locked-down SSH gateway, pure public-key auth, with
three-way conflict resolution so no one's work is ever clobbered. Compute is never
scheduled across boxes; the star coordinates *state*, not *execution*.

## Quick start

**Stand up the main** — one convergent, re-runnable command provisions the box, installs
the control plane, and declares it the source of truth:

```bash
sudo sail init --as-main
```

Authorize each engineer with the public key their `sail init --main` printed:

```bash
sail fde add mady --role member --key "ssh-ed25519 AAAA… sail-sync@madybox"
```

**Join a node:**

```bash
sudo sail init --main <main-ip>   # provision + install sail-api + generate this box's key,
                                  # and print the `sail fde add …` line for the operator
sail sync                         # pull specs + projects + shared files from main
```

> On a Mac or other thin client? `sail client <host>` points your local CLI at a box and
> forwards commands over SSH — no control plane runs locally.

## Projects

A project is one `sail.yaml` — runtimes, services, repos, agent config. The **database is
the source of truth**; the on-disk descriptor is a materialized view. `sail project create`
turns it into an isolated Incus container: runtimes installed, Podman services running
with `--restart=always`, repos cloned, agent context generated.

```bash
sail project init        # author a sail.yaml
sail project create web  # provision the container
sail project edit web    # change the definition (saves to the catalog; syncs to other boxes)
sail project connect web # SSH config for your editor
```

**The synced definition is identity-free.** Per-engineer fields are placeholders —
`${GIT_NAME}`, `${GIT_EMAIL}`, `${SSH_PUBLIC_KEY}` — resolved from *your* box at provision
time. A teammate's project commits as you and trusts only your key; no one's identity or
keys ride the sync onto another box. Tunable `resources` sync both ways and resize the
live container in place.

```yaml
# sail.yaml — the shape (every field optional but name/resources/image)
name: web
resources: { cpu: 4, memory: 12GB, disk: 150GB }
image: ubuntu/24.04
runtimes: { jdk: 25, node: 22 }
git: { name: ${GIT_NAME}, email: ${GIT_EMAIL} }     # per-developer; never synced
repos:
  - { url: "https://github.com/acme/web.git", path: web }
services:
  postgres: { image: postgres:16, ports: [5432] }
agent:
  type: claude-code
  methodology: { approach: spec-driven, verify: "mvn clean test" }
  guardrails:  { max_duration: 4h, action: snapshot-and-stop }
ssh:
  authorized_keys: [ ${SSH_PUBLIC_KEY} ]            # per-developer; never synced
```

`sudo sail project demo` spins up a bundled zero-config demo (Outline wiki) to try it end
to end.

## Specs and agents

Specs are the unit of work — agent-native (like Linear, not JIRA), held in the database,
with status, assignee, and dependencies. You manage them with `sail spec`; agents inside a
container use an in-container `spec` CLI over a bound socket, so there's one source of
truth and no sync glue.

```bash
sail spec create web --title "Stripe webhook" --assignee mady --depends-on oauth
sail spec board web         # who's on what, what's ready
sail spec dispatch web      # pick the next ready spec, launch the agent, watch guardrails
```

`dispatch` respects dependencies and assignee, auto-snapshots for rollback, and launches
the agent with full generated context. Sail is **agent-agnostic** (claude-code, codex) — so
one agent can implement and another review: enable `security_audit` / `code_review` and the
audit runs as a *different* agent at spec completion. Guardrails (`max_duration` + an
action) stop a runaway and roll back to the pre-launch snapshot.

Specs and projects edited on two boxes auto-merge when the changes are disjoint; a true
same-field clash parks for `sail conflicts` rather than overwriting anyone.

## Going deeper

- **[ARCHITECTURE.md](ARCHITECTURE.md)** — the full design: the sync engine, security
  model, control plane, and provisioning pipeline.
- Every command has `--help`. State-mutating commands support `--dry-run`; all support
  `--json`.

## Build from source

Requires JDK 25+ and Maven 3.9+.

```bash
mvn clean verify                  # build + full test suite with coverage gates
JAVA_HOME=/path/to/graalvm-jdk-25 mvn clean package -Pnative -DskipTests   # native binary
```

## License

[MIT](LICENSE)
