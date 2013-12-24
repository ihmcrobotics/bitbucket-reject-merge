package ut.com.carolynvs.stash.plugin.reject_merge_commit_hook;

import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.content.ChangesetsBetweenRequest;
import com.atlassian.stash.content.MinimalChangeset;
import com.atlassian.stash.history.HistoryService;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.i18n.I18nService;
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
    private HistoryService historyService;

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

    private void MockGetChangesets(Changeset... changesets)
    {
        Page<Changeset> pagedChanges = PageUtils.createPage(Lists.newArrayList(changesets), PageUtils.newRequest(0, 1));
        when(historyService.getChangesetsBetween(any(ChangesetsBetweenRequest.class), any(PageRequest.class))).thenReturn(pagedChanges);
    }

    @Test
    public void WhenCommit_WithMergeFromMasterToMaster_IsPushedToMaster_ItIsRejected()
    {
        Changeset mergeChangeset = buildMergeChangeset();
        MockGetChangesets(mergeChangeset);
        MockGitBranchContainsCommand("master", null);

        RejectMergeCommitHook hook = new RejectMergeCommitHook(historyService, commandFactory, i18nService);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/master", mergeChangeset.getId(), mergeChangeset.getId())
        );

        boolean isAccepted = hook.onReceive(hookContext, refChanges, hookResponse);

        assertFalse(isAccepted);
    }

    @Test
    public void WhenCommit_WithMergeFromTrunkToFeature_IsPushedToMaster_ItIsAccepted()
    {
        Changeset mergeChangeset = buildMergeChangeset();
        MockGetChangesets(mergeChangeset);
        MockGitBranchContainsCommand("feature-branch", null);

        RejectMergeCommitHook hook = new RejectMergeCommitHook(historyService, commandFactory, i18nService);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/master", mergeChangeset.getId(), mergeChangeset.getId())
        );

        boolean isAccepted = hook.onReceive(hookContext, refChanges, hookResponse);

        assertTrue(isAccepted);
    }

    @Test
    public void WhenCommit_WithMergeFromTrunkToFeature_IsPushedToFeature_ItIsAccepted()
    {
        Changeset mergeChangeset = buildMergeChangeset();
        MockGetChangesets(mergeChangeset);
        MockGitBranchContainsCommand("master", "feature-branch");

        RejectMergeCommitHook hook = new RejectMergeCommitHook(historyService, commandFactory, i18nService);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/feature-branch", mergeChangeset.getId(), mergeChangeset.getId())
        );

        boolean isAccepted = hook.onReceive(hookContext, refChanges, hookResponse);

        assertTrue(isAccepted);
    }

    @Test
    public void WhenCommit_WithoutMerge_IsPushed_ItIsAccepted()
    {
        Changeset normalChangeset = buildChangeset();
        MockGetChangesets(normalChangeset);

        RejectMergeCommitHook hook = new RejectMergeCommitHook(historyService, commandFactory, i18nService);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/feature-branch", normalChangeset.getId(), normalChangeset.getId())
        );

        boolean isAccepted = hook.onReceive(hookContext, refChanges, hookResponse);

        assertTrue(isAccepted);
    }

    @Test
    public void CommitsInFromHashAreNotIncludedInMergeChecks()
    {
        Changeset normalChangeset = buildChangeset();
        String fromHash = UUID.randomUUID().toString();
        String toHash = normalChangeset.getId();

        Page<Changeset> pagedChanges = PageUtils.createPage(Lists.newArrayList(normalChangeset), PageUtils.newRequest(0, 1));
        ArgumentCaptor<ChangesetsBetweenRequest> capturedRequest = ArgumentCaptor.forClass(ChangesetsBetweenRequest.class);
        when(historyService.getChangesetsBetween(capturedRequest.capture(), any(PageRequest.class)))
                .thenReturn(pagedChanges);

        RejectMergeCommitHook hook = new RejectMergeCommitHook(historyService, commandFactory, i18nService);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/feature-branch", fromHash, toHash)
        );

        hook.onReceive(hookContext, refChanges, hookResponse);

        ChangesetsBetweenRequest request = capturedRequest.getValue();
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

    private Changeset buildMergeChangeset()
    {
        return buildChangeset(2);
    }

    private Changeset buildChangeset()
    {
        return buildChangeset(1);
    }

    private Changeset buildChangeset(int numberOfParents)
    {
        Changeset mergeChangeset = mock(Changeset.class);

        when(mergeChangeset.getId()).thenReturn(UUID.randomUUID().toString());

        ArrayList<MinimalChangeset> parents = new ArrayList<MinimalChangeset>(numberOfParents);
        for (int i = 0; i < numberOfParents; i++)
        {
            parents.add(buildParentChangeset());
        }
        when(mergeChangeset.getParents()).thenReturn(parents);

        return mergeChangeset;
    }

    private MinimalChangeset buildParentChangeset()
    {
        MinimalChangeset parent = mock(MinimalChangeset.class);

        when(parent.getId()).thenReturn(UUID.randomUUID().toString());

        return parent;
    }
}