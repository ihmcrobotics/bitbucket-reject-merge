Reject Merge Commit Hook
========================

This is a pre-receive hook plugin for Atlassian Stash. It rejects merge commits where all the parents for the commit are from the same branch. Merges which involve multiple branches, e.g. merging a feature branch into master, are allowed. 

The goal is to prevent unnecesary merge commits, e.g. "Merge branch 'master' of mygitserver:owner/repo", from polluting a repository. For example, your team may be following a workflow which prefers using 'git pull --rebase' and this plugin will enforce that policy.
