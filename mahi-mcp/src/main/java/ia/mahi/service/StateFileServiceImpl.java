package ia.mahi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ia.mahi.workflow.core.ChangelogEntry;
import ia.mahi.workflow.core.StateSnapshot;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of StateFileService.
 * Reads and writes state.json files in spec directories.
 */
@Service
public class StateFileServiceImpl implements StateFileService {

    private static final int MAX_CHANGELOG = 200;
    private static final ConcurrentHashMap<String, Object> PATH_LOCKS = new ConcurrentHashMap<>();

    private final ObjectMapper mapper;

    public StateFileServiceImpl() {
        this.mapper = buildMapper();
    }

    @Override
    public StateSnapshot updateState(String specAbsPath, String currentPhase, ChangelogEntry changelogEntry) {
        Object lock = PATH_LOCKS.computeIfAbsent(specAbsPath, k -> new Object());
        synchronized (lock) {
            return updateStateInternal(specAbsPath, currentPhase, changelogEntry);
        }
    }

    private StateSnapshot updateStateInternal(String specAbsPath, String currentPhase, ChangelogEntry changelogEntry) {
        Path specDir = Path.of(specAbsPath);
        Path stateJson = specDir.resolve("state.json");

        try {
            ObjectNode root;
            List<ChangelogEntry> existingChangelog = new ArrayList<>();

            if (Files.exists(stateJson)) {
                root = (ObjectNode) mapper.readTree(stateJson.toFile());
                // Preserve existing changelog
                if (root.has("changelog") && root.get("changelog").isArray()) {
                    ArrayNode changelogArray = (ArrayNode) root.get("changelog");
                    for (var node : changelogArray) {
                        ChangelogEntry entry = mapper.treeToValue(node, ChangelogEntry.class);
                        existingChangelog.add(entry);
                    }
                }
            } else {
                root = mapper.createObjectNode();
                // Try to derive specId from directory name
                String specId = specDir.getFileName().toString();
                root.put("id", specId);
            }

            Instant now = Instant.now();
            root.put("currentPhase", currentPhase);
            root.put("updatedAt", now.toString());

            // Append new changelog entry if provided
            if (changelogEntry != null) {
                existingChangelog.add(changelogEntry);
            }

            // Cap changelog to prevent unbounded growth
            if (existingChangelog.size() > MAX_CHANGELOG) {
                existingChangelog = new ArrayList<>(
                        existingChangelog.subList(existingChangelog.size() - MAX_CHANGELOG, existingChangelog.size()));
            }

            ArrayNode changelogNode = mapper.createArrayNode();
            for (ChangelogEntry entry : existingChangelog) {
                changelogNode.add(mapper.valueToTree(entry));
            }
            root.set("changelog", changelogNode);

            Files.createDirectories(specDir);
            // Atomic write: write to temp file then rename to prevent partial writes on crash
            Path tmp = stateJson.resolveSibling("state.tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root);
            Files.move(tmp, stateJson, StandardCopyOption.REPLACE_EXISTING);

            String specId = root.has("id") ? root.get("id").asText() : specDir.getFileName().toString();
            return new StateSnapshot(specId, currentPhase, now, existingChangelog);

        } catch (IOException e) {
            throw new RuntimeException("Failed to update state.json at: " + stateJson, e);
        }
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return m;
    }
}
