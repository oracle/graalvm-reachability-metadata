#!/bin/bash
set -x

git config --local user.email "actions@github.com"
git config --local user.name "Github Actions"

BRANCH="libraryVersionsDependabot/$(date '+%Y-%m-%d')"
git fetch origin "$BRANCH"
git checkout "$BRANCH"

while [ true ]
do
  ./gradlew addTestedVersion --coordinates="$1" --lastSupportedVersion="$2"
  git add -u
  git commit -m "$1"
  if [ "$(git rev-list --count origin/$BRANCH --not $BRANCH)" -eq 0 ]
  then
  git push origin "$BRANCH"
  PUSH_RETVAL=$?
  if [ "$PUSH_RETVAL" -eq 0 ]
  then
    break
  fi
  fi

  git reset --hard HEAD~1
  git fetch origin "$BRANCH"
  git rebase -X theirs "origin/$BRANCH"
done


