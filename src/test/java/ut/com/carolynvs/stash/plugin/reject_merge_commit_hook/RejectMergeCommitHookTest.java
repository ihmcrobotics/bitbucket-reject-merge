package ut.com.carolynvs.stash.plugin.reject_merge_commit_hook;

import com.atlassian.stash.commit.Commit;
import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.commit.CommitsBetweenRequest;
import com.atlassian.stash.commit.MinimalCommit;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.i18n.I18nService;
import com.atlassian.stash.idx.CommitIndex;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.git.GitCommand;
import com.atlassian.stash.scm.git.GitCommandBuilderFactory;
import com.atlassian.stash.scm.git.GitScmCommandBuilder;
import com.atlassian.stash.util.*;
import com.carolynvs.stash.plugin.reject_merge_commit_hook.GitBranchListOutputHandler;
import com.carolynvs.stash.plugin.reject_merge_commit_hook.RejectMergeCommitHook;
import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.*;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.OngoingStubbing;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 * Testing {@link com.carolynvs.stash.plugin.reject_merge_commit_hook.RejectMergeCommitHook}
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
    private RepositoryHookContext hookContext;

    @Mock
    private HookResponse hookResponse;

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
        when(hookResponse.out()).thenReturn(new PrintWriter(output));
    }

    private void MockGitBranchContainsCommand(String... branches)
    {
        GitCommand<List<String>> gitBranchesCommand = (GitCommand<List<String>>) mock(GitCommand.class);
        OngoingStubbing<List<String>> when = when(gitBranchesCommand.call());
        for (String branch : branches)
        {
            ArrayList<String> result = new ArrayList<String>();
            if(branch != null)
                result.add(branch);
            when = when.thenReturn(result);
        }

        GitScmCommandBuilder gitCommandBuilder = mock(GitScmCommandBuilder.class);
        when(gitCommandBuilder.command(any(String.class))).thenReturn(gitCommandBuilder);
        when(gitCommandBuilder.argument(any(String.class))).thenReturn(gitCommandBuilder);
        when(gitCommandBuilder.build(any(GitBranchListOutputHandler.class))).thenReturn(gitBranchesCommand);

        when(commandFactory.builder(repository)).thenReturn(gitCommandBuilder);
    }

    private void MockGetCommits(Commit... commits)
    {
        Page<Commit> pagedChanges = PageUtils.createPage(Lists.newArrayList(commits), PageUtils.newRequest(0, 1));
        when(commitService.getCommitsBetween(any(CommitsBetweenRequest.class), any(PageRequest.class))).thenReturn(pagedChanges);
    }

    private void MockCommitIndex()
    {
        when(commitIndex.isMemberOf(any(String.class), any(Repository.class))).thenReturn(false);
    }

    @Test
    public void WhenCommit_WithMergeFromMasterToMaster_IsPushedToMaster_ItIsRejected()
    {
        Commit mergeCommit = buildMergeCommit();
        MockGetCommits(mergeCommit);
        MockGitBranchContainsCommand("master", null);

        RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/master", mergeCommit.getId(), mergeCommit.getId())
        );

        boolean isAccepted = hook.onReceive(hookContext, refChanges, hookResponse);

        assertFalse(isAccepted);
    }

    @Test
    public void WhenCommit_WithMergeFromTrunkToFeature_IsPushedToMaster_ItIsAccepted()
    {
        Commit mergeCommit = buildMergeCommit();
        MockGetCommits(mergeCommit);
        MockGitBranchContainsCommand("feature-branch", null);

        RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/master", mergeCommit.getId(), mergeCommit.getId())
        );

        boolean isAccepted = hook.onReceive(hookContext, refChanges, hookResponse);

        assertTrue(isAccepted);
    }

    @Test
    public void WhenCommit_WithMergeFromTrunkToFeature_IsPushedToFeature_ItIsAccepted()
    {
        Commit mergeCommit = buildMergeCommit();
        MockGetCommits(mergeCommit);
        MockGitBranchContainsCommand("master", "feature-branch");

        RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/feature-branch", mergeCommit.getId(), mergeCommit.getId())
        );

        boolean isAccepted = hook.onReceive(hookContext, refChanges, hookResponse);

        assertTrue(isAccepted);
    }

    @Test
    public void WhenCommit_WithoutMerge_IsPushed_ItIsAccepted()
    {
        Commit normalCommit = buildCommit();
        MockGetCommits(normalCommit);

        RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/feature-branch", normalCommit.getId(), normalCommit.getId())
        );

        boolean isAccepted = hook.onReceive(hookContext, refChanges, hookResponse);

        assertTrue(isAccepted);
    }

    @Test
    public void CommitsInFromHashAreNotIncludedInMergeChecks()
    {
        Commit normalCommit = buildCommit();
        String fromHash = UUID.randomUUID().toString();
        String toHash = normalCommit.getId();

        Page<Commit> pagedChanges = PageUtils.createPage(Lists.newArrayList(normalCommit), PageUtils.newRequest(0, 1));
        ArgumentCaptor<CommitsBetweenRequest> capturedRequest = ArgumentCaptor.forClass(CommitsBetweenRequest.class);
        when(commitService.getCommitsBetween(capturedRequest.capture(), any(PageRequest.class)))
                .thenReturn(pagedChanges);

        RejectMergeCommitHook hook = new RejectMergeCommitHook(commitService, commandFactory, i18nService, commitIndex);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/feature-branch", fromHash, toHash)
        );

        hook.onReceive(hookContext, refChanges, hookResponse);

        CommitsBetweenRequest request = capturedRequest.getValue();
        assertTrue(request.getExcludes().contains(fromHash));
        assertTrue(request.getIncludes().contains(toHash));
    }

    private RefChange buildRefChange(String refId, String fromHash, String toHash)
    {
        RefChange refChange = mock(RefChange.class);

        when(refChange.getRefId()).thenReturn(refId);
        when(refChange.getFromHash()).thenReturn(fromHash);
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

        return commit;
    }

    //TODO: the recursion fails because the parent commit does not really exist
    private MinimalCommit buildParentCommit()
    {
        MinimalCommit parent = mock(MinimalCommit.class);

        when(parent.getId()).thenReturn(UUID.randomUUID().toString());

        return parent;
    }
}