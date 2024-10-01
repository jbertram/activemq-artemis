#!/bin/sh

git add .
git commit -m "Your commit message"
git commit --amend --fixup=HEAD
git rebase -i --autosquash HEAD~2
git push origin HEAD --force

