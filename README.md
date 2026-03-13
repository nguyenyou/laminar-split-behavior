# Laminar `splitBoolean` Behavior Demo

This project demonstrates how `splitBoolean` works in Laminar and when to use it.

## Run

```
scala-cli --power package main.scala -f -o main.js
open index.html
```

## How `splitBoolean` works

```scala
signal.splitBoolean(
  whenTrue = { unitSignal => renderTrueBranch() },
  whenFalse = { unitSignal => renderFalseBranch() }
)
```

Under the hood, `splitBoolean` calls `splitOne(identity)`, which uses `SplitSignal` — the same memoization engine behind `.split` for lists.

The boolean value itself is the cache key. When the value changes:

1. The **old branch's cache entry is evicted** (removed entirely)
2. The **new branch's render callback is called fresh**

This means:
- **Every toggle re-runs the render callback** for the entering branch
- Consecutive emissions of the **same** value do NOT re-run the callback
- There is **no persistent cache across toggles** — unlike `.split` on a list where items that reappear can hit the cache, `splitBoolean` only ever has one key active (`true` or `false`), so the other is always evicted

## `splitBoolean` vs `child <-- signal.map(...)`

```scala
// Option A: splitBoolean
child <-- signal.splitBoolean(
  whenTrue = { _ => div("on") },
  whenFalse = { _ => div("off") }
)

// Option B: map
child <-- signal.map(if (_) div("on") else div("off"))
```

Both re-create the element on every toggle. The key differences:

| | `splitBoolean` | `.map(if/else)` |
|---|---|---|
| **Re-renders on toggle** | Yes | Yes |
| **Re-renders on same value** | No (memoized) | Yes (creates new element every emission) |
| **Inner signal** | Provides a `StrictSignal[Unit]` you can use to react to re-entries | No inner signal |
| **Unmount/mount** | Old element unmounted, new element mounted on toggle | Same behavior |

### When to use `splitBoolean`

- **You want deduplication**: If the parent signal may emit the same boolean value multiple times (e.g., from `.combineWith` or noisy sources), `splitBoolean` skips redundant re-renders. With `.map`, every emission creates a new element even if the value didn't change.
- **You need the inner signal**: The `StrictSignal[Unit]` parameter lets child components know when their branch was re-entered, useful for triggering animations or side effects.

### When `.map` is fine

- The signal is already distinct (e.g., a simple `Var` toggle) and you're rendering simple/cheap elements. The overhead difference is negligible.

### When neither is ideal

- If both branches are **expensive to create** and you want to **keep them alive** across toggles (preserve DOM state, scroll position, input values), neither approach works — both destroy and recreate. Instead, render both branches and toggle visibility:

```scala
div(
  display <-- signal.map(if (_) "" else "none"),
  expensiveTrueElement
),
div(
  display <-- signal.map(if (_) "none" else ""),
  expensiveFalseElement
)
```
