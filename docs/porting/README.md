# Porting Docs

Keep only the active parity sources in this directory.

## Current

- [`native-feature-gate.md`](native-feature-gate.md): working parity gate and feature-close criteria.
- `route-registry.json`: machine-readable route map used by `scripts/porting/verify-parity.sh`.

## Archive

- Historical wave reports, backlog snapshots, and worktree-support docs live under `docs/archive/porting/20260303/`.

## Working Rule

When parity status changes, update `native-feature-gate.md` or `route-registry.json`. Do not add new dated snapshot docs unless they are evidence that cannot live in `docs/evidence/`.
