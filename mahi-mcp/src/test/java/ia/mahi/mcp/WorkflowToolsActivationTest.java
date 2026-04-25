package ia.mahi.mcp;

import ia.mahi.service.ActiveStateService;
import ia.mahi.service.StateFileService;
import ia.mahi.workflow.core.context.ActiveState;
import ia.mahi.workflow.core.context.ChangelogEntry;
import ia.mahi.workflow.core.context.StateSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-001.3 [RED] — Tests pour les méthodes MCP dans ActiveStateTools.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowToolsActivationTest {

    @Mock
    private ActiveStateService activeStateService;

    @Mock
    private StateFileService stateFileService;

    private ActiveStateTools activeStateTools;

    @BeforeEach
    void setUp() {
        activeStateTools = new ActiveStateTools(null, activeStateService, stateFileService);
    }

    @Test
    void activate_shouldDelegateToActiveStateService() {
        ActiveState expected = new ActiveState("spec", "my-spec", "wf-uuid",
                ".mahi/specs/2026/04/my-spec", Instant.now());
        when(activeStateService.activate("my-spec", "spec", ".mahi/specs/2026/04/my-spec", "wf-uuid"))
                .thenReturn(expected);

        ActiveState result = activeStateTools.activate("my-spec", "spec", ".mahi/specs/2026/04/my-spec", "wf-uuid");

        verify(activeStateService).activate("my-spec", "spec", ".mahi/specs/2026/04/my-spec", "wf-uuid");
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getActive_whenActive_shouldReturnActiveState() {
        ActiveState expected = new ActiveState("spec", "my-spec", "wf-uuid",
                ".mahi/specs/2026/04/my-spec", Instant.now());
        when(activeStateService.getActive()).thenReturn(Optional.of(expected));

        ActiveState result = activeStateTools.getActive();

        verify(activeStateService).getActive();
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getActive_whenAbsent_shouldReturnNull() {
        when(activeStateService.getActive()).thenReturn(Optional.empty());

        ActiveState result = activeStateTools.getActive();

        verify(activeStateService).getActive();
        assertThat(result).isNull();
    }

    @Test
    void deactivate_shouldDelegateToActiveStateService() {
        activeStateTools.deactivate();

        verify(activeStateService).deactivate();
    }

    @Test
    void updateRegistry_shouldDelegateToActiveStateService() {
        activeStateTools.updateRegistry("my-spec", "spec", "design", "My Spec Title", "2026/04");

        verify(activeStateService).updateRegistry("my-spec", "spec", "design", "My Spec Title", "2026/04");
    }

    @Test
    void updateState_shouldDelegateToStateFileServiceAndReturnSnapshot() {
        StateSnapshot expected = new StateSnapshot("my-spec", "design", Instant.now(), List.of());
        when(stateFileService.updateState("/absolute/path/my-spec", "design", null))
                .thenReturn(expected);

        StateSnapshot result = activeStateTools.updateState("/absolute/path/my-spec", "design", null);

        verify(stateFileService).updateState("/absolute/path/my-spec", "design", null);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void updateState_withChangelogEntry_shouldPassEntryToService() {
        ChangelogEntry entry = new ChangelogEntry(Instant.now(), "transition",
                "Phase approved", List.of("REQ-001"));
        StateSnapshot expected = new StateSnapshot("my-spec", "design", Instant.now(), List.of(entry));
        when(stateFileService.updateState("/absolute/path/my-spec", "design", entry))
                .thenReturn(expected);

        StateSnapshot result = activeStateTools.updateState("/absolute/path/my-spec", "design", entry);

        verify(stateFileService).updateState("/absolute/path/my-spec", "design", entry);
        assertThat(result).isEqualTo(expected);
    }
}
