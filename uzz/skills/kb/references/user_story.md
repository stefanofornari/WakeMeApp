# User Story — Standard Reference

> This document is the single source of truth for how a user story must be written,
> structured, and filed in this project.

---

## 1. INVEST Principles

Every user story **must** satisfy all six INVEST criteria:

| Criterion | Meaning |
|-----------|---------|
| **I**ndependent | Deliverable without being blocked by another story (barring explicit dependencies). |
| **N**egotiable | Scope and approach can be refined through conversation with stakeholders. |
| **V**aluable | Delivers direct business or user value on its own. |
| **E**stimable | Small and well-understood enough to be sized. |
| **S**mall | Executable within **1–3 days** of work. Split complex features into thin vertical slices. |
| **T**estable | Has concrete, verifiable acceptance criteria. |

> **Vertical slicing rule**: Split complex features into thin vertical slices that cross all layers (UI → logic → persistence) rather than horizontal architectural layers. Do **not** create standalone "database" or "API framework" stories.

---

## 2. Identifier Format

Each story carries a unique identifier following the pattern:

```
[DOMAIN]/US-[NUMBER]
```

- `[DOMAIN]` — short, stable, domain code structure (e.g. `auth`, `billing`, `core/ui`).
- `[NUMBER]` — zero-padded **6-digit integer**, **global** across all domains (not per-domain).

**Examples**: `auth/US-000001`, `core/ui/US-000002`

### Determining the next ID

Scan all existing `uzz/stories/*/US-*/` folders. Take the highest `US-NUMBER` found and continue from there. The folder structure is the single source of truth — no separate counter file.

### Domain codes

- If `scope.md` labels functional domains explicitly, reuse those labels directly.
- Otherwise, derive codes from named submodules/components (e.g. `BillingService` → `billing`).
- Cross-cutting capabilities with no clear owner go under `CORE`.
- Before creating a new domain code, check for an existing `uzz/stories/<DOMAIN>/` folder covering the same area and reuse it.
- A nested structure of code domains separated by '/' can be used
---

## 3. File & Directory Layout

Each story lives in its **own dedicated file**:

```
uzz/stories/[DOMAIN]/[US-ID]/[US-ID].md
```

**Rules:**
- Parent directories must be created recursively before writing the file.
- Do **not** merge multiple stories into a single file or a generic folder root.
- Stories in the knowledge base `stories/` folder use the path `stories/[domain]/[US-ID].md` and are identified by their complete relative path (e.g. `stories/authentication/US101-dark-mode.md`).

---

## 4. Standard Template

````markdown
## [US-ID] — [Clear and Concise Title]

### User Story

```
AS A [role]
I WANT [goal]
SO THAT [benefit]
```

#### Status
[TODO | WIP | DONE | BLOCKED]

#### Dependencies
- [US-ID-1]
- [US-ID-2]

#### Technical Context & Constraints
* **Target System**: [e.g., Spring Boot backend, Web UI, Mobile app]
* **Toolchain / Rules**: [coding standards and frameworks from scope.md]
* **Phase**: [only if scope.md flagged the capability with a deferred-phase qualifier, e.g. "post-MVP"]

#### Notes
1. Technical details, implementation guidance, architectural constraints, or edge cases.
2. Do NOT put testable acceptance criteria here — keep them in the dedicated section below.

### Acceptance Criteria

```gherkin
Scenario: [Happy path scenario title]
  Given [initial context or system state]
  When  [action taken by the actor]
  Then  [expected deterministic outcome]

Scenario: [Edge case or error handling title]
  Given [initial context or system state]
  When  [invalid or boundary edge case]
  Then  [expected error, safety block, or state rollback]
```

### References
- [Reference Name](URL or relative file path)

### Keywords
- keyword-1
- keyword-2
- keyword-3
````

---

## 5. Field Specifications

### 5.1 Title
- Must be clear, concise, and capture the user intent.
- Format: `## [US-ID] — [Descriptive Title]` (e.g. `## AUTH/US-000001 — Enable Dark Mode for Improved User Experience`).

### 5.2 User Story Body
- Follow the classic `AS A / I WANT / SO THAT` format.
- Focus purely on **user intent** and **business/user value** — no implementation detail.
- Resolve `[role]` against actors defined in `users.md`. Do **not** invent unmapped roles.
  If `users.md` is missing or doesn't cover a needed role, stop and ask rather than guessing.

### 5.3 Status
| Value | Meaning |
|-------|---------|
| `TODO` | Not yet started |
| `WIP` | In progress |
| `DONE` | Implemented and verified |
| `BLOCKED` | Cannot proceed — dependency or impediment |

### 5.4 Dependencies
- Explicitly list dependent story IDs (e.g. `AUTH/US-000001`).
- Establishes build and implementation ordering.

### 5.5 Technical Context & Constraints
- Target system, toolchain references, and any phase qualifier inherited from `scope.md`.
- In-scope items with a deferred-phase qualifier (e.g. "post-MVP", "phase 2") are still written as normal stories; the qualifier is noted here so deferral stays visible.

### 5.6 Notes
- Technical details, implementation guidance, configuration parameters, edge cases.
- **Do not** place testable acceptance criteria here.

### 5.7 Acceptance Criteria
- Written as **Gherkin scenarios** (`Given / When / Then`).
- Every criterion must be **testable and verifiable**.
- Cover at least one happy-path scenario and one edge-case or error scenario.
- Keyword aliases accepted: `GIVEN / WHEN / EXPECT` when using the BDD block format.

### 5.8 References
- Links to external sources, design files, ADRs, pull requests, or issue trackers.

### 5.9 Keywords
- Placed **at the very end** of every article.
- Bulleted list of relevant tags to optimise searchability.

---

## 6. Scope Compliance Rules

- **Out-of-scope items**: Do **not** generate stories for anything explicitly listed under an "Out-of-Scope" or "Future Enhancements" heading in `scope.md`.
- **Deferred items**: In-scope items with a deferred qualifier are still generated normally; record the qualifier in **Technical Context & Constraints**.

---

## 7. Conflict Resolution (when updating existing stories)

When a story file already exists at the target path:

1. Generate the new content and **diff** it against the current file.
2. **Identical** → skip, no write needed.
3. **Different** → do **not** overwrite silently. Report the story ID and a short description of what changed, and wait for explicit confirmation before applying the update.
4. Handle each conflicting story **individually** — never batch conflicts into one blanket yes/no.

---

## 8. Domain Index

After any write/update session, keep `uzz/stories/index.md` up to date:

- One `##` section per domain.
- Inside each section, a table: `id | title | status`.
- New stories default to `TODO`; preserve the existing status of stories already listed.
