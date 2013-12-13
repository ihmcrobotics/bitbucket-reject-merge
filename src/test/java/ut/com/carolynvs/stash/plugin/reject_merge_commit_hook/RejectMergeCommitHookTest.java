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
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

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
        when(gitBranchesCommand.call()).thenReturn(Lists.newArrayList(branches));

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
    public void WhenMergeCommitWithSingleParentIsPushed_ItIsRejected()
    {
        Changeset mergeChangeset = buildMergeChangeset();
        MockGetChangesets(mergeChangeset);
        MockGitBranchContainsCommand("master");

        RejectMergeCommitHook hook = new RejectMergeCommitHook(historyService, commandFactory, i18nService);
        Collection<RefChange> refChanges = Lists.newArrayList(
                buildRefChange("refs/heads/master", mergeChangeset.getId(), mergeChangeset.getId())
        );

        boolean isAccepted = hook.onReceive(hookContext, refChanges, hookResponse);

        assertFalse(isAccepted);
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
        Changeset mergeChangeset = mock(Changeset.class);

        when(mergeChangeset.getId()).thenReturn(UUID.randomUUID().toString());
        Collection<MinimalChangeset> parents = Lists.newArrayList(
                buildParentChangeset(),
                buildParentChangeset()
        );
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