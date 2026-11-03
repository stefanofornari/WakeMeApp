# Uzzo Knowledge Base — Initialization Templates

> Seed content for `uzzo-init`. Copy each template only when the target file is
> missing or empty. Replace bracketed placeholders only with facts supplied by
> the user or established from the repository; leave the rest untouched.
>
> If the knowledge base root is not `uzz/`, rewrite the `uzz/` prefix in the
> template links (`uzz/project/...`) to match the actual root path.
>
> Every article template ends with `### Keywords`, as required by the Knowledge
> Base Management skill. These are seeds only: the `kb`, `scope`, `story`, and
> `code` skills remain authoritative whenever their guidance differs.
>
> Add an empty `.gitkeep` to each empty leaf directory (typically `library/`)
> so the directory survives version control.

---

## 1. `project/overview.md`

```markdown
# Project Overview

## Identity

- **Name**: [PROJECT NAME]
- **Version**: [VERSION]
- **Summary**: [One paragraph describing what the project is, who it serves, and the problem it solves.]

## Team & Roles

| Person | Role | Responsibilities |
|--------|------|------------------|
| [NAME] | [ROLE] | [RESPONSIBILITIES] |

## Timeline

- **Start**: [YYYY-MM-DD]
- **Milestones**:
  - [MILESTONE] — [YYYY-MM-DD]

## Repositories

- [REPOSITORY NAME]([URL]) — [PURPOSE]

## Architecture

[High-level overview of the components, their boundaries, the main data flow, and any key architectural decisions.]

### Keywords
- project
- overview
- team
- timeline
- repositories
- architecture
```

---

## 2. `project/coding_standard.md`

```markdown
# Coding Standard

## Purpose

[What this standard applies to and who must follow it.]

## General Principles

- Prefer clear, readable, and maintainable code.
- Keep components small and focused.
- Favour composition over duplication.
- Write code that is easy to test.
- [Additional principle]

## [LANGUAGE] Conventions

- [Naming, typing, immutability, visibility, or API convention]
- [Additional convention]

## Testing Conventions

- [Test framework and assertion style]
- [Test naming convention]
- [Coverage or mocking policy]

## Documentation Conventions

- [Javadoc/docstring, comment, or README expectations]
- [Additional documentation rule]

### Keywords
- coding-standard
- conventions
- style
- quality
- testing
```

---

## 3. `project/development_framework.md`

```markdown
# Development Framework

## Purpose

[How this project is built, tested, and run.]

## Technology Stack

- **Language / Runtime**: [LANGUAGE AND VERSION]
- **Frameworks / Libraries**: [FRAMEWORKS]
- **Build Tool**: [BUILD TOOL]
- **Test Tools**: [TEST TOOLS]

## Development Workflow

- [e.g. TDD: write a failing test, make it pass, refactor]
- [Branching, review, or release convention]
- [Additional workflow rule]

## Build and Execution

- **Build**: `[BUILD COMMAND]`
- **Test**: `[TEST COMMAND]`
- **Run**: `[RUN COMMAND]`

### Keywords
- development-framework
- toolchain
- build
- testing
- workflow
```

---

## 4. `stories/scope.md`

```markdown
# Project Name: [PROJECT NAME]
**Version:** [VERSION]

## Overall Description

[High-level description of the system or feature, followed by a breakdown of its main functional domains.]

Functional domains:
- **[DOMAIN NAME]**: [What this domain is responsible for]
- **[DOMAIN NAME]**: [What this domain is responsible for]

## In-Scope Capabilities

* **[DOMAIN NAME]**:
  - [ ] [Capability that MUST be delivered in the current cycle]
  - [ ] [Capability that MUST be delivered in the current cycle]

## Out-of-Scope / Future Enhancements

* [Feature or behaviour explicitly excluded from the current cycle]
* [Deferred idea that would cause scope creep]

## Technical Context & Infrastructure

* **Development Toolchain**: [e.g. Java 21 + Maven, Node.js + TypeScript]
* **Target Systems/Platforms**: [e.g. Linux desktop, Web, Spring Boot backend, iOS]
* **Project Reference Docs**:
  - Project Overview: `uzz/project/overview.md`
  - Coding Standard: `uzz/project/coding_standard.md`
  - Development Framework: `uzz/project/development_framework.md`

### Keywords
- scope
- requirements
- boundaries
- in-scope
- out-of-scope
```

---

## 5. `stories/users.md`

```markdown
# Users

Actors referenced by the `AS A [role]` clause of user stories. This list is
authoritative: stories must not invent roles that are not listed here. Add a
role only when the project genuinely has it.

- **[ROLE]**: [Who this role is and what they do with the system.]
- **[ROLE]**: [Who this role is and what they do with the system.]

### Keywords
- users
- actors
- personas
- roles
```

---

## 6. `stories/index.md`

```markdown
# Stories Index

> One `##` section per domain. Inside each section, a table with
> `id | title | status`, where status is one of `TODO`, `WIP`, `DONE`, `CLOSED`.
> New stories default to `TODO`; preserve existing statuses.

_No stories yet. Domain sections are added when user stories are generated._

### Keywords
- stories
- index
- backlog
```

---

## 7. Workflow Skill Stub

Use only when the canonical `kb`, `scope`, `story`, or `code` skill cannot be
copied from a local bundle or fetched from upstream. Create it at
`skills/<name>/SKILL.md`, mark the run as incomplete, and tell the user which
skills are stubs.

```markdown
---
name: [SKILL NAME]
description: [When this skill should be used.]
metadata:
  version: "[YYYYMMDD]"
---

# Skill: [SKILL NAME]

> **Stub — canonical instructions missing.** This file was created by
> `uzzo-init` because the canonical skill was unavailable. Replace it with the
> version shipped in the Uzzo Workflow distribution
> (`src/main/uzz/skills/[SKILL DIRECTORY]/SKILL.md`) or from
> https://github.com/stefanofornari/uzzo-workflow before relying on it.

## Goal

[One-sentence description of the skill.]

## Additional Instructions

- Follow the Knowledge Base Management rules in `../kb/SKILL.md`.
```
