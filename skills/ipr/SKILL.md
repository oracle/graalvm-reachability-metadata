---
name: ipr
description: Create a GitHub issue and pull request in oracle/graalvm-reachability-metadata, link the PR to the issue, and request reviews from kimeta, jormundur00, and vjovanov.
argument-hint: "[title-or-summary]"
---

# Issue + PR + Reviewers

Use this skill when asked to create an issue and PR for
`oracle/graalvm-reachability-metadata` and request the standard reviewer set.

Standard reviewers:

- `kimeta`
- `jormundur00`
- `vjovanov`

## Workflow

1. Confirm the working tree and branch are suitable for a PR.
   ```bash
   git status --short
   git branch --show-current
   git remote -v
   ```
   - Do not include unrelated local changes in the PR.
   - If no commits exist for the intended change, create the commit first using
     the repository's normal style.

2. Create the issue.
   - Use the user's requested title and body when provided.
   - If the user only gave a short summary, make the issue title concise and
     put the key context in the body.
   ```bash
   gh issue create \
     -R oracle/graalvm-reachability-metadata \
     --title "ISSUE_TITLE" \
     --body "ISSUE_BODY"
   ```
   - Record the created issue number and URL.

3. Push the branch.
   ```bash
   git push -u origin HEAD
   ```
   - If the repository convention requires pushing to a fork remote instead of
     `origin`, use the configured fork remote.

4. Create the PR and link it to the issue.
   - Include `Fixes #ISSUE_NUMBER` or `Closes #ISSUE_NUMBER` in the PR body so
     GitHub links the PR to the issue.
   ```bash
   gh pr create \
     -R oracle/graalvm-reachability-metadata \
     --title "PR_TITLE" \
     --body "PR_BODY

Fixes #ISSUE_NUMBER"
   ```
   - If a PR already exists for the current branch, update its body to include
     the issue link instead of creating a duplicate.

5. Request reviewers.
   ```bash
   gh pr edit PR_NUMBER \
     -R oracle/graalvm-reachability-metadata \
     --add-reviewer kimeta,jormundur00,vjovanov
   ```

6. Verify the final state.
   ```bash
   gh pr view PR_NUMBER -R oracle/graalvm-reachability-metadata --json number,url,reviewRequests,closingIssuesReferences
   gh issue view ISSUE_NUMBER -R oracle/graalvm-reachability-metadata --json number,url,state
   ```
   Confirm:
   - the PR exists and links to the created issue
   - review requests include `kimeta`, `jormundur00`, and `vjovanov`
   - the issue is open unless the PR has already been merged

## Decision Rules

- Do not guess issue or PR content when the title/body materially matters.
  Ask for the missing content if it cannot be inferred from the current branch,
  commit messages, or user request.
- If one of the standard reviewers cannot be requested because GitHub rejects
  the account or permissions, continue requesting the remaining reviewers and
  report the failure explicitly.
- If the PR already has one of the reviewers assigned, leave it as-is.
- Do not add labels, assignees, projects, or milestones unless the user asks or
  repository automation requires them.

## Output Style

Report:

- issue number and URL
- PR number and URL
- reviewers requested
- any reviewer or linking failures
