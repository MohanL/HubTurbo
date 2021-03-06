package tests;

import backend.RepoIO;
import backend.control.RepoOpControl;
import backend.github.GitHubModelUpdatesData;
import backend.github.GitHubRepoTask;
import backend.interfaces.Repo;
import backend.resource.Model;
import backend.resource.MultiModel;
import backend.resource.TurboIssue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import util.AtomicMaxInteger;
import util.Futures;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RepoOpControlTest {

    private static final Logger logger = LogManager.getLogger(RepoOpControlTest.class.getName());

    private static final String REPO = "test/test";
    private static final TurboIssue issue = new TurboIssue(REPO, 1, "Issue 1");
    private static final Optional<Integer> milestone = Optional.of(1);

    private final Executor executor = Executors.newCachedThreadPool();

    private <T> GitHubRepoTask.Result<T> createEmptyUpdatesResult() {
        return new GitHubRepoTask.Result<>(new ArrayList<>(), "", new Date());
    }

    private GitHubModelUpdatesData createEmptyModelUpdatesData(Model model) {
        return new GitHubModelUpdatesData(model, createEmptyUpdatesResult(), new ArrayList<>(),
                                          createEmptyUpdatesResult(), createEmptyUpdatesResult(),
                                          createEmptyUpdatesResult());
    }

    @Test
    public void opsWithinMultipleRepos() throws ExecutionException, InterruptedException {

        // Operations on different repositories can execute concurrently

        AtomicMaxInteger counter = new AtomicMaxInteger(0);
        RepoIO repoIO = stubbedRepoIO(counter);
        RepoOpControl control = TestUtils.createRepoOpControlWithEmptyModels(repoIO);
        repoIO.setRepoOpControl(control);

        List<CompletableFuture<Model>> futures = new ArrayList<>();

        futures.add(control.openRepository(REPO));
        futures.add(control.updateLocalModel(createEmptyModelUpdatesData(new Model(REPO + 1)), true));
        futures.add(control.openRepository(REPO));
        futures.add(control.updateLocalModel(createEmptyModelUpdatesData(new Model(REPO + 1)), true));
        control.removeRepository(REPO + 2).get();
        control.removeRepository(REPO + 2).get();

        Futures.sequence(futures).get();

        assertEquals(2, counter.getMax());
    }

    @Test
    public void opsWithinSameRepo() throws ExecutionException, InterruptedException {

        // Operations on the same repository cannot execute concurrently

        AtomicMaxInteger counter = new AtomicMaxInteger(0);
        RepoIO repoIO = stubbedRepoIO(counter);
        RepoOpControl control = TestUtils.createRepoOpControlWithEmptyModels(repoIO);
        repoIO.setRepoOpControl(control);

        List<CompletableFuture<Model>> futures = new ArrayList<>();

        futures.add(control.openRepository(REPO));
        control.removeRepository(REPO).get();
        futures.add(control.updateLocalModel(createEmptyModelUpdatesData(new Model(REPO)), true));
        control.removeRepository(REPO).get();
        futures.add(control.openRepository(REPO));
        futures.add(control.updateLocalModel(createEmptyModelUpdatesData(new Model(REPO)), true));

        Futures.sequence(futures).get();

        assertEquals(1, counter.getMax());
    }

    @Test
    public void openingSameRepo() throws ExecutionException, InterruptedException {

        // We cannot open the same repository concurrently

        AtomicMaxInteger counter = new AtomicMaxInteger(0);
        RepoOpControl control = new RepoOpControl(stubbedRepoIO(counter), mock(MultiModel.class));

        List<CompletableFuture<Model>> futures = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            futures.add(control.openRepository(REPO));
        }
        Futures.sequence(futures).get();

        assertEquals(1, counter.getMax());
    }

    @Test
    public void openingDifferentRepo() throws ExecutionException, InterruptedException {

        // We can open different repositories concurrently

        AtomicMaxInteger counter = new AtomicMaxInteger(0);
        RepoOpControl control = new RepoOpControl(stubbedRepoIO(counter), mock(MultiModel.class));

        List<CompletableFuture<Model>> futures = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            futures.add(control.openRepository(REPO + i));
        }
        Futures.sequence(futures).get();

        assertEquals(3, counter.getMax());
    }

    @Test
    public void replaceIssueMilestoneLocally() throws ExecutionException, InterruptedException {
        int issueId = 1;
        Optional<Integer> milestoneId = Optional.of(1);
        MultiModel models = mock(MultiModel.class);

        TurboIssue returnedIssue = new TurboIssue("testrepo/testrepo", issueId, "Issue title");
        returnedIssue.setMilestoneById(1);

        when(models.replaceIssueMilestone("testrepo/testrepo", issueId, milestoneId))
                .thenReturn(Optional.of(returnedIssue));

        RepoOpControl repoOpControl = new RepoOpControl(mock(RepoIO.class), models);
        Optional<TurboIssue> result = repoOpControl.replaceIssueMilestoneLocally(returnedIssue, milestoneId).join();

        assertTrue(result.isPresent());
        assertEquals(returnedIssue, result.get());
    }

    /**
     * Tests that replaceIssueLabelsLocally calls replaceIssueLabels method from models and return corresponding result
     */
    @Test
    public void replaceIssueLabelsLocally() throws ExecutionException, InterruptedException {
        MultiModel models = mock(MultiModel.class);
        TurboIssue returnedIssue = new TurboIssue("testrepo/testrepo", 1, "Issue title");
        when(models.replaceIssueLabels("testrepo/testrepo", 1, new ArrayList<>()))
                .thenReturn(Optional.of(returnedIssue));
        RepoOpControl repoOpControl = new RepoOpControl(mock(RepoIO.class), models);
        TurboIssue result = repoOpControl.replaceIssueLabelsLocally(returnedIssue, new ArrayList<>()).join().get();
        assertEquals(returnedIssue, result);
    }

    /**
     * Tests that {@code editIssueStateLocally} calls @{code editIssueState} method from models
     * and return corresponding result
     */
    @Test
    public void editIssueStateLocally() throws ExecutionException, InterruptedException {
        MultiModel models = mock(MultiModel.class);
        TurboIssue returnedIssue = new TurboIssue("testrepo/testrepo", 1, "Issue title");
        when(models.editIssueState("testrepo/testrepo", 1, false))
                .thenReturn(Optional.of(returnedIssue));
        RepoOpControl repoOpControl = new RepoOpControl(mock(RepoIO.class), models);
        TurboIssue result = repoOpControl.editIssueStateLocally(returnedIssue, false).join().get();
        assertEquals(returnedIssue, result);
    }

    /**
     * Tests that replaceIssueAssigneeLocally calls replaceIssueAssignee method from models and
     * return corresponding result
     */
    @Test
    public void replaceIssueAssigneeLocally() throws ExecutionException, InterruptedException {
        MultiModel models = mock(MultiModel.class);
        TurboIssue returnedIssue = new TurboIssue("testrepo/testrepo", 1, "Issue title");
        when(models.replaceIssueAssignee("testrepo/testrepo", 1, Optional.of("")))
                .thenReturn(Optional.of(returnedIssue));
        RepoOpControl repoOpControl = new RepoOpControl(mock(RepoIO.class), models);
        TurboIssue result = repoOpControl.replaceIssueAssigneeLocally(returnedIssue, Optional.of("")).join().get();
        assertEquals(returnedIssue, result);
    }

    /**
     * Tests that {@code editIssueStateOnServer} calls @{code editIssueState} method from RepoIO
     * and return corresponding success/failure state
     */
    @Test
    public void editIssueStateOnServer() throws ExecutionException, InterruptedException {
        RepoIO mockedRepoIO = mock(RepoIO.class);
        when(mockedRepoIO.editIssueState(any(TurboIssue.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(true));

        TurboIssue returnedIssue = new TurboIssue("testrepo/testrepo", 1, "Issue title");
        RepoOpControl repoOpControl = new RepoOpControl(mockedRepoIO, mock(MultiModel.class));
        boolean result = repoOpControl.editIssueStateOnServer(returnedIssue, false).join();
        assertEquals(true, result);
    }

    /**
     * Creates a stub RepoIO with artificial delay for various operations, and
     * which increments a value for purposes of verifying behaviour.
     */
    private RepoIO stubbedRepoIO(AtomicMaxInteger counter) {

        RepoIO stub = mock(RepoIO.class);

        when(stub.replaceIssueMilestone(issue, milestone))
                .then(invocation -> createResult(counter, new TurboIssue("dummy/dummy", 1, "Issue title")));

        when(stub.openRepository(REPO))
                .then(invocation -> createResult(counter, new Model(REPO)));
        when(stub.removeRepository(REPO))
                .then(invocation -> createResult(counter, true));
        when(stub.updateModel(new Model(REPO), false))
                .then(invocation -> createResult(counter, new Model(REPO)));

        for (int i = 0; i < 3; i++) {
            when(stub.openRepository(REPO + i))
                    .then(invocation -> createResult(counter, new Model(REPO)));
            when(stub.removeRepository(REPO + i))
                    .then(invocation -> createResult(counter, true));
            when(stub.updateModel(new Model(REPO + i), false))
                    .then(invocation -> createResult(counter, new Model(REPO)));
        }

        return stub;
    }

    /**
     * Creates a result value which completes after a short delay, to simulate an async task.
     */
    private <T> CompletableFuture<T> createResult(AtomicMaxInteger counter, T value) {
        CompletableFuture<T> result = new CompletableFuture<>();

        executor.execute(() -> {
            counter.increment();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.error(e.getLocalizedMessage(), e);
            }
            counter.decrement();
            result.complete(value);
        });

        return result;
    }
}
