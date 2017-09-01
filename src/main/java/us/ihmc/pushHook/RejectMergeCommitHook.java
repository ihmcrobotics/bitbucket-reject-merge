package us.ihmc.pushHook;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitRequest;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.commit.MinimalCommit;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHook;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHookRequest;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.idx.CommitIndex;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.RefChangeType;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;

public class RejectMergeCommitHook implements PreRepositoryHook<RepositoryHookRequest>
{
   private final CommitService commitService;
   private final GitCommandBuilderFactory commandFactory;
   private final I18nService i18nService;
   private final CommitIndex commitIndex;
   private static final String REFS_HEADS = "refs/heads/";
   private String branchName;
   private String firstBranchName;
   private String secondBranchName;

   public enum MergeAnalysis
   {
      VALID_MERGE, NOT_A_MERGE, ALREADY_PROCESSED, INCORRECT_FORMAT, ILLEGAL_MERGE_WITH_SELF;
   }
   
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
   public RepositoryHookResult preUpdate(PreRepositoryHookContext context, RepositoryHookRequest request)
   {
      final Repository repository = request.getRepository();

      for (RefChange refChange : request.getRefChanges())
      {
         //branch deletions and changes to non-branch refs (e.g. tags) can be ignored
         //new branches are examined, because they can already contain illegal merges when pushed for the first time
         if (RefChangeType.DELETE == refChange.getType() || !refChange.getRef().getId().startsWith(REFS_HEADS))
         {
            continue;
         }

         branchName = refChange.getRef().getId().substring(REFS_HEADS.length());

         MergeAnalysis mergeAnalysis = containsIllegalMergeRecursive(refChange.getToHash(), repository, branchName);
         if (mergeAnalysis == MergeAnalysis.ILLEGAL_MERGE_WITH_SELF)
         {
            String errorMessage = "Branch " + branchName + ": Cannot merge branch " + firstBranchName + " into itself (" + secondBranchName + ").";
            return RepositoryHookResult.rejected(i18nService.getText("us.ihmc.pushHook.error_message", errorMessage), errorMessage);
         }
         else if (mergeAnalysis == MergeAnalysis.INCORRECT_FORMAT)
         {
            String errorMessage = "Invalid merge message format. Please format as \"Merge branch 'branch1' into branch2$\"";
            return RepositoryHookResult.rejected(i18nService.getText("us.ihmc.pushHook.error_message", errorMessage), errorMessage);
         }
      }

      return RepositoryHookResult.accepted();
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
   private MergeAnalysis containsIllegalMergeRecursive(final String hash, final Repository repository, final String branchName)
   {
      //isMemberOf is false for newly pushed commits, true for pre-existing ones
      //if this commit is not new, it and its ancestors are already processed, there is nothing to do
      if (commitIndex.isIndexed(hash, repository))
      {
         return MergeAnalysis.ALREADY_PROCESSED;
      }

      final CommitRequest request = new CommitRequest.Builder(repository, hash).build();
      final Commit commit = commitService.getCommit(request);

      //if this commit is an illegal merge, return true.
      MergeAnalysis mergeAnalysis = isIllegalMerge(commit, repository, branchName);
      if (mergeAnalysis == MergeAnalysis.INCORRECT_FORMAT || mergeAnalysis == MergeAnalysis.ILLEGAL_MERGE_WITH_SELF)
      {
         return mergeAnalysis;
      }
      // if this commit is OK, examine parents
      for (MinimalCommit parent : commit.getParents())
      {
         MergeAnalysis mergeAnalysisRecursive = containsIllegalMergeRecursive(parent.getId(), repository, branchName);
         if (mergeAnalysisRecursive == MergeAnalysis.INCORRECT_FORMAT || mergeAnalysisRecursive == MergeAnalysis.ILLEGAL_MERGE_WITH_SELF)
         {
            return mergeAnalysisRecursive;
         }
      }
      return MergeAnalysis.VALID_MERGE;
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
   private MergeAnalysis isIllegalMerge(final Commit commit, final Repository repository, final String branchName)
   {
      // Thanks to http://stackoverflow.com/a/2081349 for how to identify the type of merge

      //not a merge commit, return quickly
      if (2 > commit.getParents().size())
      {
         return MergeAnalysis.NOT_A_MERGE;
      }

      // Check if the message shows a branch merging into itself.
      // ex. Merge branch 'develop' of https://sbertrand@stash.ihmc.us/scm/rob/ihmc-open-robotics-software.git into develop
      String message = commit.getMessage().trim().replaceAll("\\\n", " ");
      
      int openingApostrophe = message.indexOf('\'');
      int closingApostrophe = message.lastIndexOf('\'');
      
      if (openingApostrophe < 0 || closingApostrophe < 0)
      {
         return MergeAnalysis.INCORRECT_FORMAT;
      }
      
      firstBranchName = getSimpleBranchName(message.substring(openingApostrophe + 1, closingApostrophe));
      
      String[] split = message.split(" ");
      secondBranchName = getSimpleBranchName(split[split.length - 1]);
      
      if (firstBranchName.equals(secondBranchName))
      {
         return MergeAnalysis.ILLEGAL_MERGE_WITH_SELF;
      }
      
//      HashSet<String> parentBranches = new HashSet<String>();
//      for (MinimalCommit parent : commit.getParents())
//      {
//         Command<List<String>> getBranches = commandFactory.builder(repository).command("branch").argument("--contains").argument(parent.getId())
//                                                           .build(new GitBranchListOutputHandler());
//
//         List<String> branches = getBranches.call();
//         // the commit has not been pushed yet and only belongs to the branch to which we are currently pushing
//         if (null == branches || branches.size() == 0)
//         {
//            parentBranches.add(branchName);
//         }
//         else
//         {
//            parentBranches.addAll(branches);
//         }
//      }
//
//      //if all parents are in the same branch, the merge is illegal
//      if (parentBranches.size() < 2)
//      {
//         hookResponse.err().println("=================================");
//         hookResponse.err().println(String.format("%s: %s", i18nService.getText("us.ihmc.pushHook.rejected_merge_commit",
//                                                                                "Rejected merge commit: "), commit.getId()));
//         return true;
//      }
      return MergeAnalysis.VALID_MERGE;
   }
   
   private String getSimpleBranchName(String branchName)
   {
      String[] split = branchName.split("/");
      return split[split.length - 1];
   }
}