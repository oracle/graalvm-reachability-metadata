#!/bin/bash
:' This script tries to run addTestedVersion gradle task which adds new version in the tested-versions list of the proper index.json file.
Since the script could be executed from multiple parallel jobs, we want to avoid two things here: overwriting of previous changes and merge conflicts.
To prevent overwriting of changes that some job already created, we only push changes from the current job if we are 0 commits behind the origin branch.
Once that is achieved, we can try to push changes.
If the push was rejected because of a merge conflict, we are: removing changes of the current job, rebasing, and doing the process again until it succeeds.
'

set -x

git config --local user.email "actions@github.com"
git config --local user.name "Github Actions"

BRANCH="check-new-library-versions/$(date '+%Y-%m-%d')"
git fetch origin "$BRANCH"
git checkout "$BRANCH"

while [ true ]
do
  # update the list of tested versions
  ./gradlew addTestedVersion --coordinates="$1" --lastSupportedVersion="$2"

  # commit changes
  git add -u
  git commit -m "$1"

  # only push changes if we are not behind the remote branch
  if [ "$(git rev-list --count origin/$BRANCH --not $BRANCH)" -eq 0 ]
  then
    # try to push changes
    git push origin "$BRANCH"
    PUSH_RETVAL=$?
    if [ "$PUSH_RETVAL" -eq 0 ]
    then
      # if the push was successful, we can exit the loop
      break
    fi
  fi

  # we are either behind the remote branch or we have a merge conflict => remove changes and rebase accepting incoming changes
  git reset --hard HEAD~1
  git fetch origin "$BRANCH"
  git rebase -X theirs "origin/$BRANCH"
done


