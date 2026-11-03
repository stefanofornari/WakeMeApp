---
name: uzzo-init
description: Initializes a Uzzo knowledge base by creating the uzz/ directory tree (library, project, skills, stories) and seeding its core articles and workflow skills. Use when starting a new project with Uzzo Workflow, when the user asks to set up, scaffold, initialize, or repair the uzz knowledge base, or when the uzz/ folder is missing or incomplete.
metadata:
  version: "20261028"
---

# Skill: Initialize a Uzzo Knowledge Base

## Goal

Create — or safely complete — the `uzz/` knowledge base described by the
Knowledge Base Management skill (`../kb/SKILL.md`), so that scoping, story
writing, and coding can start from a consistent, non-empty structure.

This skill needs to: read and list files/directories, create nested
directories, and create or edit markdown files. Use whichever file tools are
available in the current environment — the workflow below describes what must
happen, not specific tool names.

---

## Non-Negotiable Rules

1. **Additive and idempotent** — never delete, rename, truncate, or overwrite an
   existing file or directory. If a target already exists, inspect it, leave its
   content untouched, and report it as skipped. Only create missing paths, and
   populate a file only when it is missing or empty.
2. **Target root** — default to `uzz/` at the project root. If the user names a
   different root, use it, but keep the internal four-directory layout unchanged.
3. **No root articles** — never place a `.md` file directly under the knowledge
   base root. Articles belong under `library/`, `project/`, or `stories/`;
   skill instructions belong under `skills/`.
4. **No invented facts** — use only project facts supplied by the user or
   already present in the repository. Keep unknown values as bracketed
   placeholders such as `[PROJECT NAME]` and list them in the final report. Do
   not guess a tech stack, repository URL, team member, version, or timeline.
5. **Keywords** — every seeded article under `library/`, `project/`, and
   `stories/` must end with a `### Keywords` section. `SKILL.md` files are
   instructions, not articles: they carry YAML frontmatter instead.
6. **Canonical skills are copied, not rewritten** — never paraphrase or
   regenerate the `kb`, `scope`, `story`, `code`, or `uzzo-init` skills from
   memory. Copy the canonical files when available; otherwise create the
   clearly-marked stubs defined in the templates reference.
7. **One story per file** — do not create placeholder story files. A new
   `stories/` starts with only `scope.md`, `users.md`, and `index.md`; domain
   folders and `[US-ID]` folders are created later by the story skill.
8. **No invented backlog** — leave `stories/index.md` without domain sections
   until real stories exist. Never seed fake domains, story IDs, or statuses.
9. **Conflicts stop the run** — if completing the structure would require
   changing existing content, stop, report the conflicting paths, and ask the
   user how to proceed. Do not "fix" an existing article unasked.

---

## Inputs

- **Target root** — `uzz/` unless the user specifies otherwise.
- **Known project facts** — name, version, summary, repository URL(s), team,
  timeline, and toolchain, when already provided in the conversation or visible
  in the repository (e.g. `pom.xml`, `package.json`, `README.md`).
- **Templates** — the canonical seed content is in
  [`references/templates.md`](references/templates.md). Read it before writing.
- **Skill bundle** — the canonical workflow skills are the sibling directories
  of this skill (`../kb`, `../scope`, `../story`, `../code`, `../uzzo-init`),
  or the equivalent `src/main/uzz/skills/` tree of the Uzzo Workflow
  distribution.

If project facts are missing and the user is available, ask for at most three
grouped questions (typically name, version, and repository). If the user is not
available or declines, seed placeholders and report them; do not block
initialization on them.

---

## Execution Pipeline

### Step 1: Locate and Audit

- Resolve the project root and the target knowledge base root.
- If the target exists but is a file, stop and ask.
- Inventory existing directories and files. Classify the run as:
  - **Fresh** — target is absent or empty.
  - **Repair** — target exists but lacks required directories or seed files.
  - **Complete** — everything required is present; make no writes.
- Never renumber, move, or restructure existing stories or articles.

### Step 2: Create the Directory Skeleton

Create the following tree recursively, skipping anything already present:

```
<root>/
├── library/
├── project/
├── skills/
└── stories/
```

Create the four directories even when they will initially be empty. Add an
empty `.gitkeep` to every **empty leaf directory** — a directory with no files
and no subdirectories — so it survives version control; this is required for Git
targets and harmless elsewhere. After the seeding steps this normally means
`library/`; if another directory is left empty (for example `skills/` when no
skill could be installed), add a `.gitkeep` there too. Do not add placeholder
articles just to fill a directory.

### Step 3: Seed the Project Articles

Create these files from `references/templates.md`, filling placeholders only
with facts established in Step 1:

| File | Purpose |
|------|---------|
| `project/overview.md` | Project identity, team, timeline, repositories, architecture |
| `project/coding_standard.md` | Language, style, testing, and documentation conventions |
| `project/development_framework.md` | Technology stack, workflow, build, and test commands |

If a file already exists, skip it. If it exists but is empty, populate it from
the template. Every seeded article must end with `### Keywords`.

### Step 4: Seed the Stories Articles

Create these files from `references/templates.md`:

| File | Purpose |
|------|---------|
| `stories/scope.md` | Narrative scope and In-Scope / Out-of-Scope boundaries |
| `stories/users.md` | Authoritative list of actors usable in `AS A [role]` |
| `stories/index.md` | Domain-indexed backlog table (`id`, `title`, `status`) |

Do **not** create domain directories, story folders, or story files. Do not add
domain sections to `stories/index.md`; the story skill owns that file once
stories exist.

### Step 5: Install the Workflow Skills

Copy the canonical skill directories into `<root>/skills/`, preserving any
`references/` subdirectories:

```
skills/kb/SKILL.md
skills/kb/references/user_story.md
skills/scope/SKILL.md
skills/story/SKILL.md
skills/code/SKILL.md
skills/uzzo-init/SKILL.md
skills/uzzo-init/references/templates.md
```

Resolve the source in this order:

1. The sibling directories of this skill (works when running from an installed
   knowledge base).
2. The Uzzo Workflow source/distribution (`src/main/uzz/skills/` or packaged
   `uzz` resources).
3. The upstream repository
   `https://github.com/stefanofornari/uzzo-workflow/tree/main/src/main/uzz/skills`
   when network access is available.
4. If none are available, create a stub `SKILL.md` for each missing workflow
   skill using the stub template in `references/templates.md`, and clearly mark
   the run as incomplete in the report.

Copy files verbatim. Skip existing files; if an existing skill file differs from
the canonical one, report it and leave it untouched. The shared user-story
standard at `skills/kb/references/user_story.md` is mandatory — if it cannot be
copied, the knowledge base is incomplete.

### Step 6: Validate and Report

Verify before reporting:

- The target root contains exactly the four required directories (plus hidden
  entries such as `.gitkeep`); report any unexpected entries without deleting
  them.
- Every empty leaf directory contains a `.gitkeep`.
- No `.md` file sits directly under the target root.
- Every seeded article under `library/`, `project/`, and `stories/` ends with
  `### Keywords`.
- Every directory under `skills/` contains a `SKILL.md`, and
  `skills/kb/references/user_story.md` exists.
- `stories/index.md` contains no fabricated domains or stories.
- No existing file was modified.

Report, grouped by status:

1. **Created** — directories and files written.
2. **Skipped** — existing paths left untouched.
3. **Placeholders** — bracketed values the user must fill, with the file and
   field for each.
4. **Incomplete** — anything that could not be created (for example, workflow
   skills that fell back to stubs).
5. **Next steps** — fill the placeholders, then run the `scope` skill to write
   the real scope, the `story` skill to generate the backlog, and the `code`
   skill to implement stories. Use the `kb` skill for all later article
   maintenance.

---

## Relationship to the Other Skills

| Skill | Role after initialization |
|-------|---------------------------|
| `kb` | Governs structure, naming, paths, and keywords for every article |
| `scope` | Turns the project prompt into the real `stories/scope.md` |
| `story` | Breaks the scope into INVEST stories and maintains `stories/index.md` |
| `code` | Implements a story with TDD and updates its status |
| `uzzo-init` | Creates or repairs the skeleton these skills operate on |

Initialization only builds the container. It does not scope, decompose, or
implement anything.
