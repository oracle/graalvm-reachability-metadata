---
name: close-new-library-support-pr
description: Close or reject pull requests that add support for a new library in graalvm-reachability-metadata. Use when asked to close a `library-new-request` PR and roll the linked issue back by removing assignees and changing project status from `In Progress` to `Todo` without downgrading `Done`.
argument-hint: "[pr-number-or-url]"
---

# Close `library-new-request` PRs

Use this skill when the task is to close or reject a pull request that was opened to add support for a new library.

The PR number or URL can be passed as an optional argument (for example, `1234`, `https://github.com/oracle/graalvm-reachability-metadata/pull/1234`). If the user says "close this PR" or "revert this PR" without an argument, infer the PR from surrounding context or the current branch when possible. Ask only when the PR cannot be resolved safely.

## Goal

When a new-library support PR is closed, return the linked issue to the pre-work state:

- remove all assignees from the linked issue
- move the linked project item from `In Progress` back to `Todo`
- do not change project items already marked `Done`

Project edits require GitHub auth with the `project` scope. If `gh project ...` commands fail with an authorization error, refresh auth before continuing:

```bash
gh auth refresh -s project
```

## Workflow

1. Resolve and inspect the PR.
   - Use `gh pr view <pr> --json number,title,state,labels,body,url`.
   - Confirm this is the intended PR and that it is a new-library support PR. Prefer `library-new-request`, but the skill still applies if the PR clearly adds support for one new library.
   - If the PR is already closed, still perform the linked-issue rollback if it was not done yet.

2. Find the linked issue from the PR itself.
   - Prefer GraphQL over body scraping:
     ```bash
     gh api graphql -f query='
       query {
         repository(owner: "oracle", name: "graalvm-reachability-metadata") {
           pullRequest(number: PR_NUMBER) {
             number
             title
             closingIssuesReferences(first: 10) {
               nodes {
                 number
                 title
                 state
                 assignees(first: 10) {
                   nodes {
                     login
                   }
                 }
               }
             }
           }
         }
       }'
     ```
   - Expect exactly one linked issue for a normal `library-new-request` PR.
   - If no linked issue is returned, inspect the PR body for `Fixes #...` or `Closes #...` and only continue when the target issue is unambiguous.
   - If the PR links multiple issues, stop and explain that the rollback is ambiguous.

3. Inspect the linked issue state before changing anything.
   - Use:
     ```bash
     gh issue view ISSUE_NUMBER -R oracle/graalvm-reachability-metadata --json assignees,projectItems,title,url
     ```
   - Record:
     - current assignees
     - every project item and its status
   - Do not assume there is only one assignee.

4. Remove all assignees from the linked issue.
   - If the issue has assignees, remove every current assignee:
     ```bash
     gh issue edit ISSUE_NUMBER -R oracle/graalvm-reachability-metadata --remove-assignee login1,login2
     ```
   - If `gh issue edit --remove-assignee ...` exits successfully but the assignees are still present when re-read, clear them via the REST API instead:
     ```bash
     gh api repos/oracle/graalvm-reachability-metadata/issues/ISSUE_NUMBER -X PATCH -f assignees[]=
     ```
   - If there are no assignees, leave the issue as-is.

5. Roll project status back only when it is currently `In Progress`.
   - Query the issue's project items with item IDs and field values:
     ```bash
     gh api graphql -f query='
       query {
         repository(owner: "oracle", name: "graalvm-reachability-metadata") {
           issue(number: ISSUE_NUMBER) {
             projectItems(first: 10) {
               nodes {
                 id
                 project {
                   id
                   number
                   title
                   owner {
                     __typename
                     ... on Organization { login }
                     ... on User { login }
                   }
                 }
                 fieldValues(first: 20) {
                   nodes {
                     __typename
                     ... on ProjectV2ItemFieldSingleSelectValue {
                       field {
                         ... on ProjectV2SingleSelectField {
                           id
                           name
                         }
                       }
                       name
                       optionId
                     }
                   }
                 }
               }
             }
           }
         }
       }'
     ```
   - Find each `Status` field value.
   - If `Status` is `In Progress`, change it to `Todo`.
   - If `Status` is `Done`, leave it untouched. This covers the case where another PR already completed the issue and moved it to `Done`.
   - Leave any other status unchanged unless the user explicitly asks otherwise.

6. Resolve field IDs and option IDs from the owning project before editing.
   - For the matching project, list its fields:
     ```bash
     gh project field-list PROJECT_NUMBER --owner PROJECT_OWNER --format json
     ```
   - Read the `Status` field ID plus the option IDs for `Todo`, `In Progress`, and `Done`.
   - In this repository's main board today, the project is `oracle` project `30` titled `GraalVM Reachability Metadata`, and the status names are `Todo`, `In Progress`, and `Done`. Still query live data instead of hardcoding IDs.

7. Update the project item only when rollback is needed.
   - For each project item currently at `In Progress`, set the status to the `Todo` option:
     ```bash
     gh project item-edit \
       --id ITEM_ID \
       --project-id PROJECT_ID \
       --field-id STATUS_FIELD_ID \
       --single-select-option-id TODO_OPTION_ID
     ```
   - Never replace `Done` with `Todo`.

8. Close the PR.
   - Leave a short factual closing comment if context is useful, then close it:
     ```bash
     gh pr close PR_NUMBER -R oracle/graalvm-reachability-metadata --comment "Closing this new-library support PR and rolling the linked issue back to Todo."
     ```
   - Do not delete the branch unless the user explicitly asks.

9. Verify the rollback.
   - Re-read the issue:
     ```bash
     gh issue view ISSUE_NUMBER -R oracle/graalvm-reachability-metadata --json assignees,projectItems,state,url
     ```
   - Confirm:
     - assignees are empty
     - project items that were `In Progress` are now `Todo`
     - project items that were `Done` stayed `Done`

## Decision Rules

- If another PR has already been merged and the linked issue is `Done`, close the PR if requested but do not roll the issue back to `Todo`.
- If the PR has no unambiguous linked issue, stop and explain what is missing instead of guessing.
- If the issue belongs to multiple projects, only edit the project items that are currently `In Progress`; do not rewrite unrelated statuses.
- If the PR is merged and the user asks to "revert" it, confirm whether they mean "close the follow-up work item" or a true code revert. This skill only covers PR closure and issue/project rollback, not `git revert`.

## Output Style

Keep status updates short and operational:

- identify the PR and linked issue
- state whether assignees were removed
- state which project items moved from `In Progress` to `Todo`
- explicitly note when a `Done` item was left unchanged
