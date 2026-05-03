---
name: close-human-intervention-and-failing-ci-prs
description: Close batches of graalvm-reachability-metadata pull requests that either have the `human-intervention` label or failing CI, and revert their linked issues by clearing assignees and moving project status to `Todo`.
---

# Close Human-Intervention and Failing-CI PRs

Use this skill when asked to clean up open pull requests that need human
intervention or have failing CI. In this skill, "revert" means only issue
rollback: the linked issue must have no assignees and its project `Status` must
be `Todo`. This is not a `git revert` workflow.

## Scope

Target open PRs in `oracle/graalvm-reachability-metadata` when either condition
is true:

- The PR has the `human-intervention` label.
- The PR's CI rollup is failed (`FAILURE` or `ERROR`), or an individual
  completed check has a failing conclusion such as `FAILURE`, `CANCELLED`,
  `TIMED_OUT`, or `ACTION_REQUIRED`.

Deduplicate PRs that match both conditions. Do not delete branches unless the
user explicitly asks.

## Workflow

1. Build an inventory before making changes.
   - Fetch open human-intervention PRs:
     ```bash
     gh pr list -R oracle/graalvm-reachability-metadata --state open --label human-intervention --limit 200 --json number,title,url,labels,statusCheckRollup
     ```
   - Fetch open PRs broadly enough to find failing CI:
     ```bash
     gh pr list -R oracle/graalvm-reachability-metadata --state open --limit 500 --json number,title,url,labels,statusCheckRollup
     ```
   - If the repository has more open PRs than the limit, rerun with a higher
     limit or use GraphQL pagination.
   - Treat `PENDING`, `EXPECTED`, missing, or empty checks as not yet failing.
     Do not close those PRs for CI failure alone.

2. For each target PR, re-read live state immediately before acting.
   ```bash
   gh pr view PR_NUMBER -R oracle/graalvm-reachability-metadata --json number,title,state,merged,labels,statusCheckRollup,url,body
   ```
   - Skip merged PRs and report them separately. A merged PR needs a real code
     revert decision, which is outside this skill.
   - If a PR is already closed, still try the issue rollback when the linked
     issue is unambiguous.

3. Resolve the linked issue without guessing.
   - Prefer GraphQL `closingIssuesReferences`:
     ```bash
     gh api graphql -f query='
       query {
         repository(owner: "oracle", name: "graalvm-reachability-metadata") {
           pullRequest(number: PR_NUMBER) {
             closingIssuesReferences(first: 10) {
               nodes {
                 number
                 title
                 state
               }
             }
           }
         }
       }'
     ```
   - If no linked issue is returned, inspect the PR body for an unambiguous
     `Fixes #...`, `Closes #...`, or `Resolves #...` reference.
   - If there are zero or multiple possible linked issues, close the PR only if
     it is clearly in the target set, but do not revert any issue. Report the
     missing or ambiguous issue mapping.

4. Revert the linked issue.
   - Read assignees and project items:
     ```bash
     gh issue view ISSUE_NUMBER -R oracle/graalvm-reachability-metadata --json assignees,projectItems,state,title,url
     ```
   - Remove every assignee:
     ```bash
     gh issue edit ISSUE_NUMBER -R oracle/graalvm-reachability-metadata --remove-assignee login1,login2
     ```
   - If the assignees remain after `gh issue edit`, clear them through the REST
     API:
     ```bash
     gh api repos/oracle/graalvm-reachability-metadata/issues/ISSUE_NUMBER -X PATCH -f assignees[]=
     ```
   - Set every linked project item that has a `Status` field to `Todo`. Query
     item IDs and current status:
     ```bash
     gh api graphql -f query='
       query {
         repository(owner: "oracle", name: "graalvm-reachability-metadata") {
           issue(number: ISSUE_NUMBER) {
             projectItems(first: 20) {
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
   - Resolve the `Status` field ID and `Todo` option ID from the owning
     project. Cache this per project:
     ```bash
     gh project field-list PROJECT_NUMBER --owner PROJECT_OWNER --format json
     ```
   - Update each project item:
     ```bash
     gh project item-edit \
       --id ITEM_ID \
       --project-id PROJECT_ID \
       --field-id STATUS_FIELD_ID \
       --single-select-option-id TODO_OPTION_ID
     ```

5. Close the PR.
   - Use a short factual comment matching the reason:
     ```bash
     gh pr close PR_NUMBER -R oracle/graalvm-reachability-metadata --comment "Closing this PR because CI is failing and returning the linked issue to Todo."
     ```
     ```bash
     gh pr close PR_NUMBER -R oracle/graalvm-reachability-metadata --comment "Closing this human-intervention PR and returning the linked issue to Todo."
     ```
   - If the PR was already closed, do not reopen it.

6. Verify after every mutation.
   - Re-read the issue and PR:
     ```bash
     gh issue view ISSUE_NUMBER -R oracle/graalvm-reachability-metadata --json assignees,projectItems,state,url
     gh pr view PR_NUMBER -R oracle/graalvm-reachability-metadata --json state,url
     ```
   - Confirm the PR is closed, assignees are empty, and each reverted project
     item status is `Todo`.

## Decision Rules

- Closing PRs and reverting issues are related but separate actions. Never
  guess an issue number just to complete rollback.
- For this skill, `Done` is not a protected status: if the target PR has one
  unambiguous linked issue and rollback is requested, set linked project
  statuses to `Todo`.
- Do not close a PR only because checks are pending, missing, or inconclusive.
- If `gh project ...` fails with a missing scope or authorization error, run:
  ```bash
  gh auth refresh -s project
  ```
  Then retry the project status update.
- If a target list is unexpectedly large, print the inventory and pause for
  confirmation before mutating.

## Output Style

Report a compact batch summary:

- PRs closed for `human-intervention`
- PRs closed for failing CI
- linked issues reverted to unassigned + `Todo`
- PRs skipped because they were merged, had pending CI, or had ambiguous issue
  mapping
- any verification failures that still need manual follow-up
