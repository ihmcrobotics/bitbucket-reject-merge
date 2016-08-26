package us.ihmc.pushHook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitRequest;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.commit.MinimalCommit;
import com.atlassian.bitbucket.hook.HookResponse;
import com.atlassian.bitbucket.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.bitbucket.hook.repository.RepositoryHookContext;
import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.idx.CommitIndex;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.Command;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class RejectMergeCommitHook implements PreReceiveRepositoryHook
{
   private final CommitService commitService;
   private final GitCommandBuilderFactory commandFactory;
   private final I18nService i18nService;
   private final CommitIndex commitIndex;
   private static final String REFS_HEADS = "refs/heads/";

   public RejectMergeCommitHook(final CommitService commitService, final GitCommandBuilderFactory commandFactory, final I18nService i18nService,
                                final CommitIndex commitIndex)
   {
      this.commitService = commitService;
      this.commandFactory = commandFactory;
      this.i18nService = i18nService;
      this.commitIndex = commitIndex;
   }

   /**
    * Reject pushes which contain same-branch merge commits
    * 
    * @param context context of the running hook, contains ref. to the repo
    * @param refChanges the set of changes done to the refs being pushed
    * @param hookResponse message to pass to the client, appears on console
    * @return true if commit is accepted, otherwise false
    */
   @Override
   public boolean onReceive(@Nonnull final RepositoryHookContext context, @Nonnull final Collection<RefChange> refChanges,
                            @Nonnull final HookResponse hookResponse)
   {
      final Repository repository = context.getRepository();

      for (RefChange refChange : refChanges)
      {
         //branch deletions and changes to non-branch refs (e.g. tags) can be ignored
         //new branches are examined, because they can already contain illegal merges when pushed for the first time
         if (RefChangeType.DELETE == refChange.getType() || !refChange.getRefId().startsWith(REFS_HEADS))
         {
            continue;
         }

         //this works correctly for branch names containing refs/heads as well, e.g. refs/heads/blaarefs/heads/bugfix1
         String branchName = refChange.getRefId().substring(REFS_HEADS.length());

         if (containsIllegalMergeRecursive(refChange.getToHash(), repository, branchName, hookResponse))
         {
            hookResponse.err()
                        .println(i18nService.getText("us.ihmc.pushHook.error_message",
                                                     "Trivial merge detected on local branch " + branchName + "\nPlease fix by running: git rebase origin/"
                                                   + branchName
                                                   + "\n\nNext time, use a rebase to keep a clean linear history: ie. git pull --rebase\n"));
            hookResponse.err().println("=================================");
            return false;
         }
      }

      return true;
   }

   /**
    * Examines if a commit, or any of its ancestors, is a same-branch merge
    * 
    * @param hash The commit to start from
    * @param repository the repository containing the commit
    * @param branchName the branch currently examined
    * @param hookResponse HookResponse object to interact with the user pushing the commit
    * @return true if the commit, or one of its ancestors, is a same-branch merge commit, false otherwise
    */
   private boolean containsIllegalMergeRecursive(final String hash, final Repository repository, final String branchName,
                                                 @Nonnull final HookResponse hookResponse)
   {
      //isMemberOf is false for newly pushed commits, true for pre-existing ones
      //if this commit is not new, it and its ancestors are already processed, there is nothing to do
      if (commitIndex.isIndexed(hash, repository))
      {
         return false;
      }

      final CommitRequest request = new CommitRequest.Builder(repository, hash).build();
      final Commit commit = commitService.getCommit(request);

      //if this commit is an illegal merge, return true.
      if (isIllegalMerge(commit, repository, branchName, hookResponse))
      {
         return true;
      }
      // if this commit is OK, examine parents
      for (MinimalCommit parent : commit.getParents())
      {
         if (containsIllegalMergeRecursive(parent.getId(), repository, branchName, hookResponse))
         {
            return true;
         }
      }
      return false;
   }

   /**
    * Examines if a commit is a same-branch merge
    * 
    * @param commit The commit to check
    * @param repository the repository containing the commit
    * @param branchName the branch currently examined
    * @param hookResponse HookResponse object to interact with the user pushing the commit
    * @return true if the commit is a same-branch merge commit, false otherwise
    */
   private boolean isIllegalMerge(final Commit commit, final Repository repository, final String branchName, @Nonnull final HookResponse hookResponse)
   {
      // Thanks to http://stackoverflow.com/a/2081349 for how to identify the type of merge

      //not a merge commit, return quickly
      if (2 > commit.getParents().size())
      {
         return false;
      }

      HashSet<String> parentBranches = new HashSet<String>();
      for (MinimalCommit parent : commit.getParents())
      {
         Command<List<String>> getBranches = commandFactory.builder(repository).command("branch").argument("--contains").argument(parent.getId())
                                                           .build(new GitBranchListOutputHandler());

         List<String> branches = getBranches.call();
         // the commit has not been pushed yet and only belongs to the branch to which we are currently pushing
         if (null == branches || branches.size() == 0)
         {
            parentBranches.add(branchName);
         }
         else
         {
            parentBranches.addAll(branches);
         }
      }

      //if all parents are in the same branch, the merge is illegal
      if (parentBranches.size() < 2)
      {
         hookResponse.err().println("=================================");
         hookResponse.err().println(String.format("%s: %s", i18nService.getText("us.ihmc.pushHook.rejected_merge_commit",
                                                                                "Rejected merge commit: "), commit.getId()));
         return true;
      }
      return false;
   }
}