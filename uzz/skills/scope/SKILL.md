---
name: scope-definition-reviewer
description: Evaluates, refines, and structures the scope file for a new or modified application, feature, or user story. It guides the user through an iterative clarifying phase to establish solid boundaries before outputting a standardized system profile.
meta:
    version: "20261008"
---

# Skill: Scope Definition Reviewer

## Goal
Systematically evaluate and structure a scope file for a new or modified application, feature, or user story. This skill establishes absolute implementation boundaries, clears up ambiguities, and builds a standardized system profile file.

---

## Execution Pipeline

#### Step 1: Interrogative Clarification Phase
Before producing the final structured scope profile, analyze user input for gaps, missing details, or ambiguities:
- Ask targeted clarifying questions to collect missing details.
- Present questions one by one, or in small, logically grouped clusters (maximum 3 questions at a time) to avoid overwhelming the user.
- Focus questions on unresolved requirements, platform assumptions, or ambiguous architectural constraints.
- NEVER speculate or make assumptions. If any technical stack, platform, or project reference document path is unknown, explicitly ask the user instead of defaulting.

#### Step 2: Boundary Definition & Extraction
Partition all discussed capabilities into two mutually exclusive groups:
- In-Scope Capabilities: Features, platforms, and behaviors that MUST be delivered in the current implementation cycle.
- Out-of-Scope / Future Enhancements: Features explicitly deferred, nice-to-haves, or ideas that will cause scope creep. Treat anything not explicitly listed as in-scope as out-of-scope by default.

#### Step 3: Scope Compilation Output
Once all clarifications are complete (or when the user requests the final scope markdown), generate the final scope document following EXACTLY this Markdown template format:

# Project Name: [Insert Project Name]
**Version:** [e.g., 1.0.0, 0.1.0-alpha]

## Overall Description
[Provide a high-level breakdown of the system or feature organized by its main functional domains.]

## In-Scope Capabilities
* **[Domain/Component Name]**:
  - [ ] [Capability description item 1]
  - [ ] [Capability description item 2]

## Out-of-Scope / Future Enhancements
* [Definite boundary item 1]
* [Explicitly excluded feature 2]

## Technical Context & Infrastructure
* **Development Toolchain**: [e.g., Maven + JavaFX, Go Multiplatform, Node.js + TS, Cargo + Rust]
* **Target Systems/Platforms**: [e.g., Web, Spring Boot backend, Linux Desktop, iOS, Windows]
* **Project Reference Docs**:
  - Coding Standard: `[Path or Link to coding_standard.md]`
  - Development Framework: `[Path or Link to development_framework.md]`