package com.carolynvs.stash.plugin.reject_merge_commit_hook;

import com.atlassian.stash.hook.*;
import com.atlassian.stash.hook.repository.*;
import com.atlassian.stash.repository.*;
import com.atlassian.stash.content.Change;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.history.HistoryService;
import com.atlassian.stash.util.*;

import com.google.common.collect.*;
import java.util.Collection;
import java.util.Vector;

public class PreventMergeCommitHook implements PreReceiveRepositoryHook
{
	private static final PageRequestImpl PAGE_REQUEST = new PageRequestImpl(0, 100);
	private final HistoryService historyService;
	
	public PreventMergeCommitHook(HistoryService historyService) {
		this.historyService = historyService;
	}
	
    /**
     * Reject pushes which contain merge commits
     */
    @Override
    public boolean onReceive(RepositoryHookContext context, Collection<RefChange> refChanges, HookResponse hookResponse)
    {
		Repository repository = context.getRepository();
		boolean rejectPush = containsMergeCommit(repository, refChanges);
		
		if(rejectPush) {
			hookResponse.out().println("=================================");
			hookResponse.out().println("You may not push merge commits. Use rebase to achieve a linear history and try again.");
			hookResponse.out().println("=================================");
			return false;
		}
		
		return true;
    }
	
	private boolean containsMergeCommit(Repository repository, Collection<RefChange> refChanges){
		Iterable<Changeset> changeSets = getChanges(refChanges, repository);
		
		for(Changeset changeSet : changeSets) {
			if(isMergeCommit(changeSet)) {
				return true;
			}
		}
		
		return false;
	}
	
	private boolean isMergeCommit(Changeset changeset)
	{
		return changeset.getParents().size() > 1;
	}
	
	private Iterable<Changeset> getChanges(Iterable<RefChange> refChanges, final Repository repository) {
		Vector<Changeset> changesets = new Vector<Changeset>();
		
		for(RefChange refChange : refChanges) {
			for(Changeset changeset : getChangesetsBetween(repository, refChange)) {
				changesets.add(changeset);
			}
		}
		
		return changesets;
	}

	private Iterable<Changeset> getChangesetsBetween(final Repository repository, final RefChange refChange) {
		return new PagedIterable<Changeset>(new PageProvider<Changeset>() {
			@Override
			public Page<Changeset> get(PageRequest pageRequest) {
				return historyService.getChangesetsBetween(repository, refChange.getFromHash(), refChange.getToHash(), pageRequest);
			}
		}, PAGE_REQUEST);
	}
}
