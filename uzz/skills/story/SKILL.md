---
name: user-story-generator
description: Analyzes a scope.md file (and an optional users.md for persona/actor mapping) to break its functional domains into actionable, INVEST-compliant user stories with BDD/Gherkin acceptance criteria. Writes one markdown file per story under uzz/stories/[DOMAIN]/[US-ID]/, plus a domain-indexed index.md. Use this skill whenever the user asks to generate, break down, split, or update user stories from a scope or requirements document, mentions INVEST criteria or Gherkin/BDD acceptance criteria, or wants a scope.md turned into a backlog of stories — even if they don't say "user story generator" by name.
metadata:
  version: "20261003"
---

# Skill: Generate User Stories from Scope

## Goal
Analyze a `scope.md` file and systematically break down its functional domains into actionable, **INVEST-compliant user stories** complete with **BDD (Given/When/Then) acceptance criteria**.

This skill needs to: read files, create nested directories, and create/edit markdown files. Use whichever file-reading and file-writing tools are available in the current environment — the workflow below is described in terms of what needs to happen, not specific tool names.

---

## Execution Rules & Standards

1. **INVEST Principle**: Every user story must be Independent, Negotiable, Valuable, Estimable, Small (executable within 1–3 days of work), and Testable. Split complex features into thin vertical slices rather than horizontal architectural layers (e.g., do not create standalone "database" or "API framework" stories).
2. **Strict Scope Compliance**: Do **NOT** generate user stories for any items, systems, or features explicitly listed under an "Out-of-Scope" or "Future Enhancements" heading in `scope.md`. An in-scope item that carries a deferred-phase qualifier (e.g. "post-MVP", "phase 2") is still in-scope — generate its story as normal, but note the qualifier under **Technical Context & Constraints** so the deferral stays visible.
3. **Actor Matching**: Resolve the user roles in the `As a [User Role]` segment by reading and matching actors defined inside `users.md`. Do not invent unmapped user roles. If `users.md` is missing or doesn't cover a needed role, stop and ask rather than guessing.
4. **Identifier Formatting**: Assign a unique identifier to each story using the pattern `[DOMAIN]/US-[NUMBER]`, where `[NUMBER]` is a zero-padded, **6-digit integer** and the counter is **global across all domains** (not per-domain) — e.g. `AUTH/US-000001`, then `BILLING/US-000002`.

---

## Execution Pipeline

### Step 1: Parse & Map Domains
* **Ingest Assets**: Read and parse `scope.md` and `users.md`.
* **Map Vectors**: Extract distinct functional domains, platform environments, constraints, and valid user roles.
* **Derive Domain Codes**: If `scope.md` already labels functional domains explicitly, use those labels directly. Otherwise, derive short, stable, uppercase domain codes from the named submodules/components in the Overall Description and In-Scope Capabilities (e.g. a submodule called "BillingService" → `BILLING`). Group cross-cutting capabilities with no obvious owning submodule (e.g. a shared data model, persistence) under a `CORE` domain unless a clearer split is obvious. Before inventing a new domain code, check for an existing `uzz/stories/<DOMAIN>/` folder covering the same area and reuse it rather than creating a near-duplicate.

### Step 2: Story Decomposition
* **Slice Vertically**: Evaluate every in-scope functional domain. Slice capabilities into the smallest independent blocks that provide direct business value.
* **Format Structure**: Frame statements tightly around the standard format:
  `AS A [role], I WANT [capability], SO THAT [value/benefit]`

### Step 3: Assign IDs and Persist
* **Determine the next ID**: Scan all existing `uzz/stories/*/US-*/` folders, across every domain, and take the highest `US-NUMBER` found. Continue numbering from there. Do not maintain a separate counter file — the folder structure is the single source of truth, so there is nothing that can drift out of sync with it.
* **Check for conflicts before writing**: For each story, if `uzz/stories/[DOMAIN]/[US-ID]/[US-ID].md` already exists, generate the new content and diff it against the current file content.
  - Identical → skip, no write needed.
  - Different → do **not** overwrite. Report the story ID and a short description of what changed, and wait for explicit confirmation before applying that specific update. Handle each conflicting story individually; never batch several conflicts into one blanket yes/no.
* **Write new stories**: For every story with no existing file, create the parent directories recursively and write the file at:
  `uzz/stories/[DOMAIN]/[US-ID]/[US-ID].md`
* **Keywords update**: Make sure the Keywords, References, Dependencies are properly set/updated

### Step 4: Review and Update index.md
After a user story writing/updating session, update `uzz/stories/index.md` so it reflects the current state:
- One section per domain, domain name as a second-level heading.
- In each section, a table of that domain's stories: `id | title | status`, where status is one of `TODO`, `WIP`, `DONE`, `CLOSED`. New stories default to `TODO`; preserve the existing status of any story already in the README.

---

## 📄 Output File Template

For the full template, field specifications, INVEST principles, identifier format, Gherkin acceptance criteria rules, scope compliance, conflict resolution, and README maintenance standards, refer to the shared reference:

→ **[User Story Standard Reference](../kb/references/user_story.md)**

---

## Additional Operational Instructions
* **Directory Creation**: Ensure parent directories for the domain and the `US-ID` are generated recursively before writing the file.
* **No Merged Files**: Do not lump multiple user stories together into a single file or a generic folder root. Every story demands its own folder and dedicated markdown document.
