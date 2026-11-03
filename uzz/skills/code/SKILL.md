---
name: implement-user-story
description: Safely and systematically evaluates, plans, and implements a vertical user story using TDD while updating an active notes.md file and the story's status. Use whenever the user asks to implement, build, or code up a user story (especially one produced by user-story-generator), or asks to work a story from spec to tested code.
metadata:
  version: "20260929"
---

# Skill: Implement User Story

## Goal
Safely and systematically implement a user story from specification to tested code, adhering to project coding standards and architectural rules.

This skill needs to: read and edit files, create files, list directories, and run shell/build commands (to execute the test suite). Use whichever such tools are available in the current environment.

## Input Requirements
- A target user story identified by its ID (e.g. `AUTH/US-000012`), located at `uzz/stories/[DOMAIN]/[US-ID]/[US-ID].md`, with its title, description, and BDD acceptance criteria.
- Project context: `scope.md`, plus the coding-standard and development-framework docs it points to under "Project Reference Docs" (fall back to the project's own convention if `scope.md` doesn't specify one).

---

## Execution Pipeline

### Step 1: Story Size & Complexity Assessment
Before writing any code, evaluate the user story against the **INVEST** principles:
- **Sizing Check:** Is this story too large for a single implementation cycle (estimated > 2-3 days of work)?
- **Domain Check:** Does it modify multiple unrelated system domains simultaneously?
- **Action:** If **YES**, halt implementation. Propose a breakdown into 2+ smaller, independent vertical user stories and ask the user for confirmation before proceeding. If **NO**, proceed to Step 2.

### Step 2: Mark In Progress, Then Gather Context & Rules
- Update the story's row in `uzz/stories/index.md` to status `WIP`, so the backlog reflects that work has started before diving in.
- Inspect the target codebase and existing domain specifications.
- Read the coding-standard and development-framework docs to ensure design alignment.
- Identify all affected files, modules, and dependencies.

### Step 3: Implementation Strategy (Technical Plan)
Draft a concise execution plan covering:
1. **Target Files:** List files to be created, modified, or refactored.
2. **Data / State Changes:** Any schema updates, state updates, or API contract updates.
3. **Edge Case Handling:** Plan for potential failure points defined in acceptance criteria.

### Step 4: Test-Driven Implementation (red, green, triangulate, refactor)
1. **Red:** Pick the simplest acceptance scenario. Write one failing test for it — nothing else yet.
2. **Green, the obvious way:** Write the most obvious code that makes it pass, even if that's a constant or hard-coded return. Do not generalize ahead of a test that demands it — an untested generalization is exactly the code TDD exists to avoid writing.
3. **Triangulate:** Write a second test whose expected result the current (possibly constant) implementation cannot satisfy — a different input, a case that shouldn't trigger the behavior, or, for anything involving more than one item, a multi-item case where only one should be affected. If it fails, generalize the implementation just enough to pass both tests — no further. If it happens to already pass, the implementation was already general enough; move to the next scenario instead of adding redundant tests. Repeat one differentiating test at a time until every acceptance scenario is covered.
4. **Run the tests:** run specific tests during the development of one piece of functionality; run the full class's tests once that scenario is done, to catch regressions.
5. **Refactor:** clean up the implementation to strictly match the rules in the coding-standard doc without breaking passing tests.

### Step 5: Acceptance & Scope Verification
- Run the full test suite to ensure no regressions occurred.
- Perform a line-by-line self-check against the user story's acceptance criteria.
- Confirm no "Out-of-Scope" features accidentally leaked into the implementation.
- If everything above passes, update the story's status in `uzz/stories/index.md` to `DONE`. If anything fails, leave the status at `WIP` and report exactly what's still failing.
- Output a summary of modified files and test results for developer review.

## Additional Instructions
- **Never** try to inspect jars or decompile/javap classes without flagging it first — that's a strong smell of information that should be available elsewhere. Instead, **stop** and **ask** where such information can be found.
- Track comments, technical decisions, design choices, and trade-offs in a `notes.md` file at `uzz/stories/[DOMAIN]/[US-ID]/notes.md`, alongside the user story file.
- Do not add redundant information to `notes.md` — nothing that's already covered in the coding-standard doc, the development-framework doc, or any other generic project description.