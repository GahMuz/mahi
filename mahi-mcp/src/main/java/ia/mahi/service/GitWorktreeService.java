package ia.mahi.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Manages git worktrees for workflow isolation.
 * Each flow gets its own branch and worktree directory.
 */
@Service
public class GitWorktreeService {

    public String createWorktree(String flowId) {
        String branch = "mahi/" + flowId;
        String path = ".worktrees/" + flowId;
        run("git", "worktree", "add", "-b", branch, path);
        return path;
    }

    public void removeWorktree(String path) {
        run("git", "worktree", "remove", path, "--force");
    }

    private void run(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int code = process.waitFor();
            if (code != 0) {
                throw new IllegalStateException(
                        "Command failed: " + String.join(" ", command) + "\n" + output);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute git command", e);
        }
    }
}
