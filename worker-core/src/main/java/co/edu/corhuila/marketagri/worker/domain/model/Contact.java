package co.edu.corhuila.marketagri.worker.domain.model;

import java.util.UUID;

/** Where a notice goes: the answer of I-07 of auth (ADR-016 point 4). */
public record Contact(UUID id, String email, String name) {
}
