# AGENTS

Guidelines for agents working in the GraalVM tickets project, the `graal`
namespace.

## Rules

- This project holds only the `GR` kind: public snapshots of the GraalVM
  tickets that the repository's workarounds cite
  (§root/PRCPL-graalvm-workaround-tickets). A ticket is GraalVM's fact, not the
  repository's, so it keeps its Jira ID in a namespace of its own; grund would
  also refuse a `GR` kind beside the repository's `GRUND`, since no citable
  kind may be a prefix of another.
- Cite a ticket as `<§>graal/GR-<number>` from every namespace. Under
  `tests/src` and `real-use-tests/src`, where the marker cannot appear, name the
  bare `GR-<number>`.
- Never write or edit a snapshot by hand, and never allocate a `GR` ID with
  `grund id`: run `grund fetch graal/GR-<number>` from the repository root. It
  runs [fetch-graalvm-ticket.py](fetch-graalvm-ticket.py), which reads the
  ticket through Oracle's `gdev-cli` and so needs access to the GraalVM Jira.
  grund drops the script's stderr, so when `grund fetch` fails, run
  `graalvm-tickets/fetch-graalvm-ticket.py GR-<number>` directly to see why.
- The script refuses a security-restricted ticket, a summary carrying a URL,
  and a status or resolution that is not a plain name; its self-tests run with
  `python3 graalvm-tickets/test_fetch_graalvm_ticket.py`.
- When the last workaround citing a ticket goes, delete its snapshot in the
  same change; grund would otherwise only warn that it is never cited.
- The repository is public and the tracker is internal: a snapshot carries only
  the summary, status, resolution, release-numbered fix versions, and links to
  public `oracle/graal` and `oracle/graalvm-reachability-metadata` issues and
  pull requests. Never add the description, comments, people, or internal URLs.
- Run `grund check` from the repository root; CI never fetches. The gate also
  fails a bare `GR-<number>` in a test-source comment that has no snapshot.

<!-- BEGIN GRUND MANAGED BLOCK -->
## Grounding with grund (block v39)

This project uses [`grund`](https://github.com/agent-grounds/grund): every spec, goal, decision, and end-to-end test has a stable ID `<KIND>-<slug>[.<section>]` (`KIND ∈ {GR}`), cited with the marker `§` — e.g. `<§>FS-user-login.3.1`, where the ID is a shape illustration rather than a real one in this repo, which is what the `<§>` escape says. Type `$$` in a grund-aware editor and it becomes `§`. Bare ID-shaped tokens are ignored — `[reference] strict = true` is set in `grund.toml`, so only `§`-prefixed citations are checked.

### Grounding from a citation

A `§<ID>` is a pointer to a fact, not a file path. Resolve it with `grund` and climb only as far as needed:

- `grund <ID>` — the lead (heading-less, cut at the first child section). The cheap first read for a bare `§<ID>` citation.
- `grund <ID> --toc` — the lead plus the nested section map. Use to choose which subsection to fetch next.
- `grund <ID> --full` — the entire body. Escalate to this when narrower reads aren't enough.
- `grund <ID> --brief` — heading + first paragraph only.
- `grund refs <ID>` — every site that cites the ID; add `--summary` for one line per file. Run before renaming or moving a declaration.
  After the rename or move, edit the declaration and affected authored citation IDs, then run `grund fmt --write` from the config/project root or workspace root. When cross-reference emission applies, it re-derives generated Markdown link destinations and anchors; the root-scoped run also updates eligible section locks. Run `grund check` and `grund fmt --check`, then commit the lock changes. Locks are keyed by ID, so a file move alone does not rename one. fmt does not replace authored IDs, repair arbitrary URLs, or silently approve a protected section move; fix citations or leave a pointer for that move first. See [Maintaining declarations](https://github.com/agent-grounds/grund/blob/main/docs/user-facing/structure.md#maintaining-declarations).
- `grund list` / `grund list --kind GR` — discover IDs if you get lost
- `grund list --unused` — the declarations nothing cites yet, in one call
- `grund list --size=words --top 10` — find heavy leads and move detail into citable child points before reading them in full
- `grund list --glob '<KIND>-*' --total` — count the coordinates a pattern selects; `grund show --batch --glob '<KIND>-*' --toc --format json` then reads each one's section map in the same scan, from `.result.sections[]` (`path`, `title`, `depth`). `*` matches within one component, `**` any number of whole components, and `{a,b}` either alternative. More recipes: [Querying grund](https://github.com/agent-grounds/grund/blob/main/docs/user-facing/querying.md).

### Project map

- [GR](tickets.md): GraalVM tickets that workarounds cite (fetched snapshots)

### Project namespaces

A namespace is a project boundary, not a docs folder. The current project is the local namespace: cite its IDs as `§<ID>`.

Create or use a separate namespace when work introduces an independently checked app, package, service, or subproject. Give that project its own `grund.toml`, add it to the workspace root's `[workspace] members`, run `grund init` there, and set a stable `project_name`.

Do not create a namespace for a regular module or component that still belongs to this project. Cite across namespaces as `§alias/<ID>` and run `grund check` from the workspace root.

### Workspace members

Cross-project citations use §alias/<ID>, one alias segment per workspace level.

- [`forge`](../forge/): Automation subproject that turns labeled issues into reviewed metadata pull requests
- [`real-use-tests`](../real-use-tests/): Realistic library scenarios run on the JVM and Native Image against the shipped metadata
- [`root`](../): Repository for shared GraalVM reachability metadata, tests, and release infrastructure

### Declarations and citations

Declarations are heading lines `# <ID>: …` in markdown. In a code doc-comment (Rustdoc, Javadoc, JSDoc, Python docstring, Go `//`, …) drop the `#` — write `/// <ID>: …` directly. Numbered headings inside a declaration are citable sections: use depth-matching headings (`## 1. …`, `### 1.1 …`, etc.) so `§<ID>.1` / `§<ID>.1.1` resolve; mismatched heading depth is a `grund check` error. In a declaration body, Markdown or doc-comment, a heading may instead write only the component it adds and take the rest from the heading one level up: `### .1. Scope` under `## 3. Third` declares `§<ID>.3.1`, the one coordinate it is cited and reported by. After citing such a heading, run `grund fmt --write` so the section lock records it, and commit the lock. Every non-declaration heading inside a Markdown declaration body must carry a numbered or enabled named section path; file titles, headings that close the body, fenced examples, non-ATX text, and source doc-comments stay exempt, while bold labels remain the non-citable alternative. One doc-comment may declare multiple IDs (e.g. an `AR-` and an `FS-` on the same class) — each gets its own body. An inline source declaration is reachable from the configured kind home via a one-line stub: `# <ID>: [<path>](<path>)`.

### Rules

- **Spec first.** For behavior or design changes, write or update the most-specific spec point before code.
- **Cite as you write.** Place `§<ID>` at the point a claim or behavior is made — on the doc-comment for a whole behavior, inline beside the clause it enforces.
- **Marker = live citation.** A `§`-prefixed token resolves and is checked wherever it appears — including inside Markdown backticks. To mention an ID without citing it, write `<§><ID>`, omit the marker, or use a fenced code block.
- **Inline citation style.** Inline notes: ≤ 1 line preferred, hard cap 3 lines; ≤ 100 columns. A note is one comment block: a blank line splits it, an empty comment line does not. Doc-comments (`///`, `//!`, `/** */`, a docstring, a Go, Ruby, shell, SQL or Lisp comment right above a definition) are documentation, not notes: they are never measured, so cite in-sentence there.
- **Always cite the most-specific point.**

### Citation directions

`must`/`never` are `grund check` errors; `should`/`avoid` are suggestions (`grund check --suggestions`).

Anything not listed above is allowed.

### Clickable citations

On repository web surfaces, link `§<ID>` to the PR branch in PR bodies, the reviewed commit in reviews, an exact commit for permalinks, and the default branch otherwise; fall back to plain when unsure.
<!-- END GRUND MANAGED BLOCK -->
