package vega.vcs.infrastructure.service;

import vega.vcs.domain.repository.Repository;
import vega.vcs.domain.service.*;
import vega.vcs.domain.service.*;

/**
 * Main Vega service implementation
 * Orchestrates all Vega operations using dependency injection with Git compliance
 * Follows Dependency Inversion Principle and Single Responsibility Principle
 */
public class VegaServiceImpl implements VegaService {

    private final Repository repository;
    private final FileService fileService;
    private final CommitService commitService;
    private final StatusService statusService;
    private final VegaCheckoutService gitCheckoutService;
    private final VegaMergeService gitMergeService;
    private final FileStateService fileStateService;
    private final VegaValidationService validationService;
    private final VegaHooksService hooksService;
    private final VegaConfigService configService;

    public VegaServiceImpl(Repository repository, String defaultAuthor) {
        this.repository = repository;
        this.fileService = new FileServiceImpl(repository);
        this.commitService = new CommitServiceImpl(repository, defaultAuthor);
        this.statusService = new StatusServiceImpl(repository);

        // Initialize Git-compliant services
        this.fileStateService = new FileStateServiceImpl(repository);
        this.validationService = new VegaValidationServiceImpl(repository, fileStateService);
        this.gitCheckoutService = new VegaCheckoutServiceImpl(repository, fileStateService, validationService);
        this.gitMergeService = new VegaMergeServiceImpl(repository, fileStateService, validationService);

        // Initialize Git-specific services
        this.hooksService = new VegaHooksService(repository);
        this.configService = new VegaConfigService(repository);
    }

    @Override
    public void init() throws Exception {
        repository.init();

        // Create Vega hooks
        hooksService.createDefaultHooks();

        // Set default Vega config
        configService.setConfig("core.repositoryformatversion", "0");
        configService.setConfig("core.filemode", "true");
        configService.setConfig("core.bare", "false");
        configService.setConfig("core.logallrefupdates", "true");
        configService.setConfig("core.ignorecase", "true");
        configService.setConfig("core.precomposeunicode", "true");

        System.out.println("Initialized empty Vega repository in " + repository.getDhaDir());
    }

    @Override
    public void add(String filePath) throws Exception {
        // Validate file operations
        validationService.validateFileOperations();
        validationService.validateFileAdd(filePath);
        fileService.addFile(filePath);
    }

    @Override
    public void commit(String message) throws Exception {
        // Validate commit
        validationService.validateCommit();

        // Execute pre-commit hook
        if (!hooksService.executePreCommitHook()) {
            System.out.println("Pre-commit hook failed");
            return;
        }

        // Execute commit-msg hook
        if (!hooksService.executeCommitMsgHook(message)) {
            System.out.println("Commit-msg hook failed");
            return;
        }

        commitService.createCommit(message);
        String commitHash = "commit-hash"; // TODO: Get actual commit hash

        // Execute post-commit hook
        hooksService.executePostCommitHook(commitHash);

        System.out.println("Committed: " + commitHash);
    }

    @Override
    public void status() throws Exception {
        statusService.showStatus();
    }

    @Override
    public void log() throws Exception {
        commitService.showLog();
    }

    @Override
    public void diff(String filePath) throws Exception {
        statusService.showDiff(filePath);
    }

    @Override
    public void diff(String filePath, String mode) throws Exception {
        if (mode.equals("--side-by-side")) {
            ((StatusServiceImpl) statusService).showSideBySideDiff(filePath);
        } else if (mode.equals("--word")) {
            statusService.showDiff(filePath); // For now, use regular diff
            System.out.println("Word-level diff not yet implemented in this mode");
        } else {
            statusService.showDiff(filePath);
        }
    }

    @Override
    public void checkout(String target) throws Exception {
        gitCheckoutService.checkout(target);
    }

    @Override
    public void checkoutFile(String filePath) throws Exception {
        gitCheckoutService.checkoutFile(filePath);
    }

    @Override
    public void branch(String name) throws Exception {
        gitCheckoutService.createBranch(name);
    }

    @Override
    public void listBranches() throws Exception {
        gitCheckoutService.listBranches();
    }

    @Override
    public void merge(String branchName) throws Exception {
        gitMergeService.merge(branchName);
    }
}
