package com.carolynvs.stash.plugin.reject_merge_commit_hook;

import com.atlassian.stash.hook.*;
import com.atlassian.stash.hook.repository.*;
import com.atlassian.stash.repository.*;
import com.atlassian.stash.content.*;
import com.atlassian.stash.history.*;
import com.atlassian.stash.scm.*;
import com.atlassian.stash.scm.git.*;
import com.atlassian.stash.util.*;
import com.google.common.collect.*;
import java.util.*;
import com.atlassian.stash.i18n.I18nService;

public class RejectMergeCommitHook implements PreReceiveRepositoryHook
{
	private static final PageRequestImpl PAGE_REQUEST = new PageRequestImpl(0, 100);
	private final HistoryService historyService;
	private final GitCommandBuilderFactory commandFactory;
    private final I18nService i18nService;

	public RejectMergeCommitHook(HistoryService historyService, GitCommandBuilderFactory commandFactory, I18nService i18nService) {
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
		Iterable<Changeset> mergeCommits = findMergeCommits(repository, refChanges);
		boolean rejectPush = containsSingleBranchMergeCommit(repository, mergeCommits, hookResponse);
		
		if(rejectPush) {
			hookResponse.out().println("=================================");
			hookResponse.out().println(i18nService.getText("com.carolynvs.stash.plugin.reject_merge_commit_hook.error_message", "Merge commits where all parents are from the same branch are not allowed."));
			hookResponse.out().println("=================================");
			return false;
		}
		
		return true;
    }
	
	private boolean containsSingleBranchMergeCommit(Repository repository, Iterable<Changeset> mergeCommits, HookResponse hookResponse) {
		// Thanks to http://stackoverflow.com/a/2081349 for how to identify the type of merge

        for(Changeset mergeCommit : mergeCommits) {

			Collection<MinimalChangeset> parents = mergeCommit.getParents();

			for(MinimalChangeset parent : parents) {
				String parentId = parent.getId();
				
				GitBranchListOutputHandler handler = new GitBranchListOutputHandler();
				Command<List<String>> getBranches = commandFactory.builder(repository).command("branch").argument("--contains").argument(parentId).build(handler);

                List<String> sourceBranches = getBranches.call();

                if(sourceBranches.size() == 0) {
                    hookResponse.out().println(String.format("%s: %s", i18nService.getText("com.carolynvs.stash.plugin.reject_merge_commit_hook.rejected_merge_commit", "Rejected merge commit"), mergeCommit.getId()));
                    return true;
                }
			}
		}
		
		return false;
	}
	
	private Iterable<Changeset> findMergeCommits(Repository repository, Collection<RefChange> refChanges){
		Vector<Changeset> mergeCommits = new Vector<Changeset>();
		Iterable<Changeset> changeSets = getChanges(refChanges, repository);
		
		for(Changeset changeSet : changeSets) {
			if(isMergeCommit(changeSet)) {
				mergeCommits.add(changeSet);
			}
		}
		
		return mergeCommits;
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