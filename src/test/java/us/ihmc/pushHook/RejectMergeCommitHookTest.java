package us.ihmc.pushHook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.OngoingStubbing;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitRequest;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.commit.MinimalCommit;
import com.atlassian.bitbucket.hook.ScmHookDetails;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHookRequest;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.hook.repository.RepositoryHookTrigger;
import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.idx.CommitIndex;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.git.command.GitCommand;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.bitbucket.scm.git.command.GitScmCommandBuilder;
import com.google.common.collect.Lists;

import junit.framework.TestCase;

/**
 * Testing {@link us.ihmc.pushHook.RejectMergeCommitHook}
 */
@RunWith(MockitoJUnitRunner.class)
public class RejectMergeCommitHookTest extends TestCase
{
   @Mock
   private CommitService commitService;

   @Mock
   private CommitIndex commitIndex;

   @Mock
   private Repository repository;

   @Mock
   private GitCommandBuilderFactory commandFactory;

   @Mock
   private I18nService i18nService;

   @Mock
   private PreRepositoryHookContext hookContext;

   @Mock
   private RepositoryHookResult hookResponse;

   @Before
   public void Setup()
   {
      MockRepository();
      MockHookResponse();
      MockCommitIndex();
   }

   private void MockRepository()
   {
      when(hookContext.getRepository()).thenReturn(repository);
   }

   private void MockHookResponse()
   {
      StringWriter output = new StringWriter();
      //        when(hookResponse.out()).thenReturn(new PrintWriter(output));
      //        when(hookResponse.err()).thenReturn(new PrintWriter(output));
   }

   private void MockGitBranchContainsCommand(String... branches)
   {
      GitCommand<List<String>> gitBranchesCommand = (GitCommand<List<String>>) mock(GitCommand.class);
      OngoingStubbing<List<String>> when = when(gitBranchesCommand.call());
      for (String branch : branches)
      {
         ArrayList<String> result = new ArrayList<String>();
         if (branch != null)
            result.add(branch);
         when = when.thenReturn(result);
      }

      GitScmCommandBuilder gitCommandBuilder = mock(GitScmCommandBuilder.class);
      when(gitCommandBuilder.command(any(String.class))).thenReturn(gitCommandBuilder);
      when(gitCommandBuilder.argument(any(String.class))).thenReturn(gitCommandBuilder);
      when(gitCommandBuilder.build(any(GitBranchListOutputHandler.class))).thenReturn(gitBranchesCommand);

      when(commandFactory.builder(repository)).thenReturn(gitCommandBuilder);
   }

   private void MockCommit(final Commit commit)
   {
      final String commitId = commit.getId();

      when(commitService.getCommit(argThat(new ArgumentMatcher<CommitRequest>()
      {
         @Override
         public boolean matches(CommitRequest o)
         {
            if (o == null)
               return false;

            CommitRequest request = (CommitRequest) o;
            return request.getCommitId().equals(commitId);
         }
      }))).thenReturn(commit);
   }

   private void MockCommitIndex()
   {
      when(commitIndex.isIndexed(any(String.class), any(Repository.class))).thenReturn(false);
   }

   //    @Ignore
   @Test
   public void WhenCommitMergingDevelopIntoDevelop_ItIsRejected()
   {
      Commit mergeCommit = buildMergeCommit();
      MockGitBranchContainsCommand("develop", "develop");

      RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
      final Collection<RefChange> refChanges = Lists.newArrayList(buildRefChange("refs/heads/develop", mergeCommit.getId(), mergeCommit.getId()));

      hookResponse = hook.preUpdate(hookContext, createRequest(refChanges, repository));

      assertFalse(hookResponse.isAccepted());
   }

   //    @Ignore
   @Test
   public void WhenCommit_WithMergeFromMasterToMaster_IsPushedToMaster_ItIsRejected()
   {
      Commit mergeCommit = buildMergeCommit();
      MockGitBranchContainsCommand("master", null);

      RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
      Collection<RefChange> refChanges = Lists.newArrayList(buildRefChange("refs/heads/master", mergeCommit.getId(), mergeCommit.getId()));

      hookResponse = hook.preUpdate(hookContext, createRequest(refChanges, repository));

      assertFalse(hookResponse.isAccepted());
   }

   //    @Ignore
   @Test
   public void WhenCommit_WithMergeFromTrunkToFeature_IsPushedToMaster_ItIsAccepted()
   {
      Commit mergeCommit = buildMergeCommit();
      MockGitBranchContainsCommand("feature-branch", null);

      RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
      Collection<RefChange> refChanges = Lists.newArrayList(buildRefChange("refs/heads/master", mergeCommit.getId(), mergeCommit.getId()));

      hookResponse = hook.preUpdate(hookContext, createRequest(refChanges, repository));

      assertTrue(hookResponse.isAccepted());
   }

   //    @Ignore
   @Test
   public void WhenCommit_WithMergeFromTrunkToFeature_IsPushedToFeature_ItIsAccepted()
   {
      Commit mergeCommit = buildMergeCommit();
      MockGitBranchContainsCommand("master", "feature-branch");

      RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
      Collection<RefChange> refChanges = Lists.newArrayList(buildRefChange("refs/heads/feature-branch", mergeCommit.getId(), mergeCommit.getId()));

      hookResponse = hook.preUpdate(hookContext, createRequest(refChanges, repository));

      assertTrue(hookResponse.isAccepted());
   }

   @Test
   public void WhenCommit_WithoutMerge_IsPushed_ItIsAccepted()
   {
      Commit normalCommit = buildCommit();

      RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
      Collection<RefChange> refChanges = Lists.newArrayList(buildRefChange("refs/heads/feature-branch", normalCommit.getId(), normalCommit.getId()));

      hookResponse = hook.preUpdate(hookContext, createRequest(refChanges, repository));

      assertTrue(hookResponse.isAccepted());
   }

   private RepositoryHookRequest createRequest(final Collection<RefChange> refChanges, final Repository repository)
   {
      return new RepositoryHookRequest()
      {
   
         @Override
         public boolean isDryRun()
         {
            return false;
         }
   
         @Override
         public RepositoryHookTrigger getTrigger()
         {
            return null;
         }
   
         @Override
         public Optional<ScmHookDetails> getScmHookDetails()
         {
            return null;
         }
   
         @Override
         public Repository getRepository()
         {
            return repository;
         }
   
         @Override
         public Collection<RefChange> getRefChanges()
         {
            return refChanges;
         }
   
         @Override
         public Map<String, Object> getContext()
         {
            return null;
         }
      };
   }

   private RefChange buildRefChange(String refId, String fromHash, String toHash)
   {
      RefChange refChange = mock(RefChange.class);

      when(refChange.getRef().getId()).thenReturn(refId);
      //        when(refChange.getFromHash()).thenReturn(fromHash);
      when(refChange.getToHash()).thenReturn(toHash);

      return refChange;
   }

   private Commit buildMergeCommit()
   {
      return buildCommit(2);
   }

   private Commit buildCommit()
   {
      return buildCommit(1);
   }

   private Commit buildCommit(int numberOfParents)
   {
      Commit commit = mock(Commit.class);

      when(commit.getId()).thenReturn(UUID.randomUUID().toString());

      ArrayList<MinimalCommit> parents = new ArrayList<MinimalCommit>(numberOfParents);
      for (int i = 0; i < numberOfParents; i++)
      {
         parents.add(buildParentCommit());
      }
      when(commit.getParents()).thenReturn(parents);
      MockCommit(commit);
      return commit;
   }

   //TODO: the recursion fails because the parent commit does not really exist
   private Commit buildParentCommit()
   {
      Commit parent = mock(Commit.class);

      when(parent.getId()).thenReturn(UUID.randomUUID().toString());
      MockCommit(parent);
      return parent;
   }

   public static void main(String[] args)
   {
      String commitMessage = "Merge branch 'develop' of https://bshrewsbury@stash.ihmc.us/scm/rob/ihmc-open-robotics-software.git into origin/poop/develop";

      String message = commitMessage.trim().replaceAll("\\\n", " ");

      int openingApostrophe = message.indexOf('\'');
      int closingApostrophe = message.lastIndexOf('\'');
      String firstBranchName = getSimpleBranchName(message.substring(openingApostrophe + 1, closingApostrophe));

      String[] split = message.split(" ");
      String secondBranchName = getSimpleBranchName(split[split.length - 1]);

      System.out.println(firstBranchName + " " + secondBranchName + " " + firstBranchName.equals(secondBranchName));
   }

   private static String getSimpleBranchName(String branchName)
   {
      String[] split = branchName.split("/");
      return split[split.length - 1];
   }
}