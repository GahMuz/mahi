package ia.mahi.workflow.core;

/**
 * Represents a coherence violation between requirements and design elements.
 * Types: "AC_ORPHAN" | "DES_NO_AC" | "REQ_NO_AC" | "AC_NOT_FOUND"
 */
public record CoherenceViolation(
        String type,    // "AC_ORPHAN" | "DES_NO_AC" | "REQ_NO_AC" | "AC_NOT_FOUND"
        String itemId,  // ID of the offending element
        String message  // Human-readable message in French
) {
}
