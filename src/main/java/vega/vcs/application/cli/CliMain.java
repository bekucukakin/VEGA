package vega.vcs.application.cli;

import vega.vcs.domain.service.VegaService;
import vega.vcs.infrastructure.service.FileRepositoryImpl;
import vega.vcs.infrastructure.service.VegaServiceImpl;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Vega CLI Application
 * Entry point for the command line interface
 * Follows Single Responsibility Principle
 */
public class CliMain {

    public static void main(String[] args) throws Exception {
        // Eğer args varsa, komut satırı modunda çalış
        if (args.length > 0) {
            handleCommandLineArgs(args);
            return;
        }

        // Başlangıç dizini
        Path currentDir = Paths.get("").toAbsolutePath();

        // Dependency injection setup with Vega VCS
        FileRepositoryImpl repository = new FileRepositoryImpl(currentDir);
        VegaService vegaService = new VegaServiceImpl(repository, System.getProperty("user.name", "unknown"));

        try (Scanner sc = new Scanner(System.in)) {
            System.out.println("Vega CLI. Type 'vega help' for commands.");
            while (true) {
                System.out.print(currentDir + " > "); // prompt olarak bulunduğun dizini göster
                if (!sc.hasNextLine()) break;
                String line = sc.nextLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = splitArgs(line);
                String cmd = parts[0];
                try {
                    switch (cmd) {
                        case "exit":
                        case "quit":
                            System.out.println("bye");
                            return;
                        case "vega":
                            if (parts.length < 2) {
                                System.out.println("Vega VCS - Type 'vega help' for commands");
                            } else {
                                handleVegaCommand(parts, vegaService);
                            }
                            break;
                        case "help":
                            printHelp();
                            break;
                        case "cd":
                            if (parts.length < 2) {
                                System.out.println("usage: cd <path>");
                            } else {
                                Path newDir = currentDir.resolve(parts[1]).normalize();
                                if (Files.exists(newDir) && Files.isDirectory(newDir)) {
                                    currentDir = newDir.toAbsolutePath();
                                    repository = new FileRepositoryImpl(currentDir);
                                    vegaService = new VegaServiceImpl(repository, System.getProperty("user.name", "unknown"));
                                } else {
                                    System.out.println("No such directory: " + parts[1]);
                                }
                            }
                            break;
                        case "pwd":
                            System.out.println(currentDir.toAbsolutePath());
                            break;
                        case "echo":
                            handleEcho(parts);
                            break;
                        case "rm":
                            handleRemove(parts);
                            break;
                        case "init":
                            vegaService.init();
                            break;
                        case "add":
                            if (parts.length < 2) System.out.println("usage: add <file>");
                            else vegaService.add(parts[1]);
                            break;
                        case "commit":
                            String msg = parseCommitMessage(parts);
                            if (msg == null) System.out.println("usage: commit -m \"message\"");
                            else vegaService.commit(msg);
                            break;
                        case "status":
                            vegaService.status();
                            break;
                        case "diff":
                            if (parts.length < 2) {
                                System.out.println("usage: diff <file> [--side-by-side|--word]");
                            } else {
                                String diffMode = parts.length > 2 ? parts[2] : "";
                                vegaService.diff(parts[1], diffMode);
                            }
                            break;
                        case "log":
                            vegaService.log();
                            break;
                        case "checkout":
                            if (parts.length < 2)
                                System.out.println("usage: checkout <commit|branch> or checkout -- <file>");
                            else if (parts.length == 3 && parts[1].equals("--")) {
                                vegaService.checkoutFile(parts[2]);
                            } else {
                                vegaService.checkout(parts[1]);
                            }
                            break;
                        case "branch":
                            if (parts.length < 2) {
                                vegaService.listBranches();
                            } else {
                                vegaService.branch(parts[1]);
                            }
                            break;
                        case "merge":
                            if (parts.length < 2) System.out.println("usage: merge <branch>");
                            else vegaService.merge(parts[1]);
                            break;
                        default:
                            System.out.println("unknown command: " + cmd);
                    }
                } catch (Exception ex) {
                    System.out.println("error: " + ex.getMessage());
                    ex.printStackTrace(System.out);
                }
            }
        }
    }

    // echo komutu
    private static void handleEcho(String[] parts) {
        if (parts.length < 2) {
            System.out.println();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (i > 1) sb.append(" ");
            sb.append(parts[i]);
        }
        String echoOutput = sb.toString();

        if (echoOutput.contains(" > ")) {
            String[] redirectParts = echoOutput.split(" > ", 2);
            if (redirectParts.length == 2) {
                String content = redirectParts[0].trim();
                String filename = redirectParts[1].trim();

                try {
                    Path file = Paths.get(filename);
                    Files.write(file, content.getBytes());
                    System.out.println("Created file: " + filename);
                } catch (Exception e) {
                    System.out.println("Error creating file: " + e.getMessage());
                }
            } else {
                System.out.println(echoOutput);
            }
        } else {
            System.out.println(echoOutput);
        }
    }

    // rm komutu
    private static void handleRemove(String[] parts) {
        if (parts.length < 2) {
            System.out.println("usage: rm <file>");
            return;
        }
        try {
            Path file = Paths.get(parts[1]);
            if (Files.exists(file)) {
                Files.delete(file);
                System.out.println("Deleted file: " + parts[1]);
            } else {
                System.out.println("File not found: " + parts[1]);
            }
        } catch (Exception e) {
            System.out.println("Error deleting file: " + e.getMessage());
        }
    }

    private static void handleCommandLineArgs(String[] args) throws Exception {
        Path currentDir = Paths.get("").toAbsolutePath();
        FileRepositoryImpl repository = new FileRepositoryImpl(currentDir);
        VegaService vegaService = new VegaServiceImpl(repository, System.getProperty("user.name", "unknown"));

        handleVegaCommand(args, vegaService);
    }

    private static void handleVegaCommand(String[] parts, VegaService vegaService) throws Exception {
        if (parts.length < 2) {
            System.out.println("Vega VCS - Type 'vega help' for commands");
            return;
        }

        String subCommand = parts[1];
        String[] subArgs = new String[parts.length - 2];
        System.arraycopy(parts, 2, subArgs, 0, subArgs.length);

        switch (subCommand) {
            case "init":
                vegaService.init();
                System.out.println("Initialized empty Vega repository in " + Paths.get("").toAbsolutePath() + "/.vega");
                break;
            case "add":
                if (subArgs.length == 0) {
                    System.out.println("usage: vega add <file>");
                } else {
                    vegaService.add(subArgs[0]);
                }
                break;
            case "commit":
                String msg = parseCommitMessage(subArgs);
                if (msg == null) {
                    System.out.println("usage: vega commit -m \"message\"");
                } else {
                    vegaService.commit(msg);
                }
                break;
            case "status":
                vegaService.status();
                break;
            case "diff":
                if (subArgs.length == 0) {
                    System.out.println("usage: vega diff <file>");
                } else {
                    vegaService.diff(subArgs[0]);
                }
                break;
            case "log":
                vegaService.log();
                break;
            case "checkout":
                if (subArgs.length == 0) {
                    System.out.println("usage: vega checkout <commit|branch>");
                } else {
                    vegaService.checkout(subArgs[0]);
                }
                break;
            case "branch":
                if (subArgs.length == 0) {
                    vegaService.listBranches();
                } else {
                    vegaService.branch(subArgs[0]);
                }
                break;
            case "merge":
                if (subArgs.length == 0) {
                    System.out.println("usage: vega merge <branch>");
                } else {
                    vegaService.merge(subArgs[0]);
                }
                break;
            case "help":
                printHelp();
                break;
            default:
                System.out.println("Unknown command: " + subCommand);
                System.out.println("Type 'vega help' for available commands");
        }
    }

    private static void printHelp() {
        System.out.println("Vega VCS Commands:");
        System.out.println("  vega init                 initialize repository");
        System.out.println("  vega add <file>           add file to staging (use 'vega add .' for all files)");
        System.out.println("  vega commit -m \"msg\"     commit staged changes");
        System.out.println("  vega status               show status");
        System.out.println("  vega diff <file>          show file differences");
        System.out.println("  vega log                  show commits");
        System.out.println("  vega checkout <commit|branch>  checkout commit or branch");
        System.out.println("  vega branch [name]        list branches or create branch at HEAD");
        System.out.println("  vega merge <branch>       merge branch into current branch");
        System.out.println("  vega help                 show this help");
        System.out.println();
        System.out.println("Interactive Commands:");
        System.out.println("  cd <dir>                  change directory");
        System.out.println("  pwd                       print current directory");
        System.out.println("  echo <text>               print text to console");
        System.out.println("  rm <file>                 delete file");
        System.out.println("  help, exit                show help or exit");
    }

    private static String[] splitArgs(String line) {
        List<String> out = new ArrayList<>();
        boolean inQuote = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (!inQuote && Character.isWhitespace(c)) {
                if (sb.length() > 0) {
                    out.add(sb.toString());
                    sb.setLength(0);
                }
            } else sb.append(c);
        }
        if (sb.length() > 0) out.add(sb.toString());
        return out.toArray(new String[0]);
    }

    private static String parseCommitMessage(String[] parts) {
        for (int i = 0; i < parts.length; i++) {
            if ("-m".equals(parts[i]) && i + 1 < parts.length) return parts[i + 1];
        }
        return null;
    }
}
