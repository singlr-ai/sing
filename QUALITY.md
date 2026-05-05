# Quality Gates

`sail` treats quality gates as ratchets: each gate protects the best current baseline while pure logic is extracted from process-heavy boundaries and raised toward 100% coverage.

## Local Validation

Run the same command CI runs before opening a PR:

```bash
mvn clean verify
```

For a focused test loop, run the affected module and test classes first, then finish with full verification:

```bash
mvn -pl sail-infra -am -Dtest=CliJsonTest,CliCommandTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn clean verify
```

## Coverage Policy

- API contract classes under `ai.singlr.sing.api.*` require 100% line and method coverage.
- Pure CLI runtime utilities have explicit class gates. `CliCommand` requires 100% line, method, and branch coverage. `CliJson` requires 100% method coverage and high line/branch coverage, with only defensive reflection failure paths outside the current threshold.
- Every module has bundle-level line, method, branch, and class gates set to the current verified baseline so coverage cannot silently regress.
- Generated code, unreachable defensive paths, and external process boundaries may use staged thresholds only when full coverage would encourage brittle tests.

## Ratchet Rules

- New pure domain services, validators, DTO mappers, renderers, and command metadata should target 100% line and method coverage in the same PR that introduces them.
- Infrastructure gateways should be covered through fakes before raising their gates.
- Do not lower a gate without documenting the reason in the PR.
