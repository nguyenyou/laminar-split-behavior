# Laminar `split` Family — Cache & Re-render Behavior

Investigation into whether Laminar/Airstream's split operators keep rendered trees in memory or discard them when keys disappear.

## Run the demo

```
scala-cli --power package main.scala -f -o main.js
open index.html
```

## TL;DR — split does NOT keep the tree in memory

All split variants use the same `SplitSignal` engine. When a key disappears from the input, its cache entry is **immediately evicted** (`memoized.remove(key)`). When the key reappears, the `project` callback is called again from scratch. There is no config to change this.

## The split family

### Group 1: Collection splits (`Signal[List[A]]`, etc.)

| Operator | Description |
|---|---|
| `splitSeq` | Key each item, render once per key |
| `splitSeqByIndex` | Use index as key |
| `splitSeqMutate` | Var-only, mutable in-place updates |
| `splitSomeSeq` / `splitSomeSeqByIndex` | For `Signal[Option[List[A]]]` |

### Group 2: Single-value splits (`Signal[A]`)

All delegate to `splitOne` internally.

| Operator | Keys | Delegates to |
|---|---|---|
| `splitOne` | User-defined key function | Core `SplitSignal` |
| `splitBoolean` | `true` / `false` | `splitOne(identity)` |
| `splitEither` | `Left` / `Right` | `splitOne` |
| `splitTry` | `Success` / `Failure` | `splitOne` |
| `splitStatus` | `Resolved` / `Pending` | `splitOne` |
| `splitOption` | `Some` / `None` | `splitOne` |

### Group 3: Stream-specific

| Operator | Description |
|---|---|
| `splitStart` | Like `splitOne` for streams, with an initial value |

## Cache lifecycle (same for ALL variants)

All variants feed into `SplitSignal`, which maintains:

```
memoized: Map[Key, (Input, Signal[Input], Output, lastParentUpdateId)]
```

The lifecycle:

1. **Key appears** → `project` callback called once, result cached under that key
2. **Same key emitted again** → cached result reused, callback NOT re-called
3. **Key disappears** → cache entry **immediately evicted** (observer removed, `memoized.remove(key)`)
4. **Key reappears later** → treated as brand new, `project` called again from scratch

There is **no option to retain evicted entries**. No config, no flag — eviction is hardcoded in `SplitSignal.scala`.

## What this means in practice

| Scenario | Behavior |
|---|---|
| List item stays across emissions (key persists) | Tree **kept**, inner signal updates cheaply |
| List item removed then re-added (same key) | Tree **discarded and rebuilt** |
| Boolean toggled (`splitBoolean`) | Only 1 key active at a time; **re-renders on every toggle** |
| Option goes `None→Some→None→Some` | `Some` branch **rebuilt** each time |
| Same value emitted consecutively | Cached, callback **not** re-called |

### Where split saves you work

Split shines when **keys persist across emissions**. For a list of 100 items where 1 item changes:
- `splitSeq`: only the changed item's inner signal fires; 99 items untouched
- `.map(_.map(render))`: all 100 re-rendered

### Where split doesn't help

For binary/enum splits (`splitBoolean`, `splitEither`, `splitOption`, etc.), only one key is active at a time, so the other is always evicted. Every state change triggers a full re-render of the entering branch.

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
| **Inner signal** | Provides a `StrictSignal[Unit]` to react to re-entries | No inner signal |
| **Unmount/mount** | Old element unmounted, new mounted | Same |

### When to use `splitBoolean`

- **Deduplication**: Parent signal may emit the same boolean value multiple times (e.g., from `.combineWith` or noisy sources). `splitBoolean` skips redundant re-renders; `.map` creates a new element every emission.
- **Inner signal**: The `StrictSignal[Unit]` lets children react to branch re-entry (animations, side effects).

### When `.map` is fine

- Signal is already distinct (e.g., a simple `Var` toggle) and elements are cheap. Overhead difference is negligible.

## Keeping expensive trees alive

None of the split operators support this. Use the **visibility toggle pattern**:

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

Both elements stay mounted in the DOM. This preserves scroll position, input state, focus, etc. The cost is that both branches are always in the DOM (just hidden).
