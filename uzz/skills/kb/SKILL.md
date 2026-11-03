---
name: kb-man
description: Manage and structure knowledge base articles across library, project, and user story folders. Use when creating, updating, or organizing markdown documentation, requirements, and user stories.
meta:
  version: "20261002"
---

# Knowledge Base Management

Standard instructions and guidelines for structuring, organizing, and writing markdown articles within the knowledge base directory structure.

## Knowledge Base Structure

The knowledge base consists of articles written in Markdown, organized into three primary top-level directories:

- **`library/`**: General reference information, development practices, programming language guides, frameworks, and standard definitions.
- **`project/`**: Project-specific information such as name, version, summary, team roles/people, timeline, online repositories, and architectural overviews.
- **`skills/`**: Uzzo workflow skills
- **`stories/`**: User stories (implemented, in progress, or planned). Each file represents exactly **one** user story.

### File & Path Rules

- **No direct root files**: Do not place articles directly under the root folder. All articles must reside within `library/`, `project/`, `stories/`, or their subdirectories.
- **Subdirectories**: Categorize articles into nested subdirectories under the main folders as appropriate.
- **Full Relative Path References**: Identify and reference articles by their complete relative path (e.g., `stories/authentication/US101-dark-mode.md`).
- **Keywords Requirement**: Every knowledge base article MUST conclude with a `### Keywords` section containing relevant search and categorization tags.

---

## User Story Specification (`stories/`)

Each article in `stories/` must contain exactly **one** user story.

For the complete template, field specifications, INVEST principles, identifier format, acceptance criteria rules, and all authoring standards, refer to the shared reference:

→ **[User Story Standard Reference](references/user_story.md)**

### User Stories Scope

The overall scope of the project or of a domain is summarized in a `scope.md` document under `stories/` or the domain's folder.
Unlike `index.md`, which is a structured table of stories, `scope.md` is a descriptive narrative that summarizes the user stories contained in the folder. Its goal is to serve as a first read that gives enough context to understand the domain without having to open each individual story file.