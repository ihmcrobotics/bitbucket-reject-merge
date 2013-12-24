package com.carolynvs.stash.plugin.reject_merge_commit_hook;

import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.content.ChangesetsBetweenRequest;
import com.atlassian.stash.content.MinimalChangeset;
import com.atlassian.stash.history.HistoryService;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.i18n.I18nService;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.Command;
import com.atlassian.stash.scm.git.GitCommandBuilderFactory;
import com.atlassian.stash.util.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

public class RejectMergeCommitHook implements PreReceiveRepositoryHook
{
	private static final PageRequestImpl PAGE_REQUEST = new PageRequestImpl(0, 100);
	private final HistoryService historyService;
	private final GitCommandBuilderFactory commandFactory;
    private final I18nService i18nService;

	public RejectMergeCommitHook(HistoryService historyService, GitCommandBuilderFactory commandFactory, I18nService i18nService)
    {
		this.historyService = historyService;
		this.commandFactory = commandFactory;
        this.i18nService = i18nService;
	}
	
    /**
     * Reject pushes which contain merge commits
     */
    @Override
    public boolean onReceive(RepositoryHookContext context, Collection<RefChange> refChanges, HookResponse hookResponse)
    {
        Repository repository = context.getRepository();

        for(RefChange refChange : refChanges)
        {
            String destinationRef = refChange.getRefId().replace("refs/heads/", "");
            Iterable<Changeset> mergeCommits = findMergeCommits(repository, refChange);
            boolean rejectPush = containsSingleBranchMergeCommit(repository, destinationRef, mergeCommits, hookResponse);

            if(rejectPush) {
                hookResponse.out().println("=================================");
                hookResponse.out().println(i18nService.getText("com.carolynvs.stash.plugin.reject_merge_commit_hook.error_message", "Merge commits where all parents are from the same branch are not allowed."));
                hookResponse.out().println("=================================");
                return false;
            }
        }

		return true;
    }

	private boolean containsSingleBranchMergeCommit(Repository repository, String destinationRef, Iterable<Changeset> mergeCommits, HookResponse hookResponse)
    {
		// Thanks to http://stackoverflow.com/a/2081349 for how to identify the type of merge

        GitBranchListOutputHandler handler = new GitBranchListOutputHandler();

        for(Changeset mergeCommit : mergeCommits)
        {
            HashSet<String> parentBranches = new HashSet<String>();
			for(MinimalChangeset parent : mergeCommit.getParents())
            {
				Command<List<String>> getBranches =
                        commandFactory.builder(repository)
                        .command("branch").argument("--contains").argument(parent.getId())
                        .build(handler);

                List<String> branches = getBranches.call();
                if(branches.size() == 0) // the changeset has not been pushed yet and only belongs to the branch to which we are currently pushing
                {
                    parentBranches.add(destinationRef);
                }
                else
                {
                    parentBranches.addAll(branches);
                }
			}

            if(parentBranches.size() < 2)
            {
                hookResponse.out().println(String.format("%s: %s", i18nService.getText("com.carolynvs.stash.plugin.reject_merge_commit_hook.rejected_merge_commit", "Rejected merge commit"), mergeCommit.getId()));
                return true;
            }
		}

		return false;
    }
	
	private Iterable<Changeset> findMergeCommits(Repository repository, RefChange refChange)
    {
		Iterable<Changeset> changes = getChanges(repository, refChange);

        Vector<Changeset> mergeCommits = new Vector<Changeset>();
		for(Changeset change : changes)
        {
			if(isMergeCommit(change))
            {
				mergeCommits.add(change);
			}
		}
		
		return mergeCommits;
	}
	
	private boolean isMergeCommit(Changeset changeset)
	{
		return changeset.getParents().size() > 1;
	}
	
	private Iterable<Changeset> getChanges(Repository repository, RefChange refChange)
    {
		Vector<Changeset> changes = new Vector<Changeset>();
        for(Changeset changeset : getPagedChanges(repository, refChange))
        {
            changes.add(changeset);
        }
		
		return changes;
	}

	private Iterable<Changeset> getPagedChanges(final Repository repository, final RefChange refChange)
    {
		return new PagedIterable<Changeset>(new PageProvider<Changeset>() {
			@Override
			public Page<Changeset> get(PageRequest pageRequest)
            {
                ChangesetsBetweenRequest betweenRequest = new ChangesetsBetweenRequest.Builder(repository)
                        .exclude(refChange.getFromHash())
                        .include(refChange.getToHash())
                        .build();
				return historyService.getChangesetsBetween(betweenRequest, pageRequest);
			}
		}, PAGE_REQUEST);
	}
}