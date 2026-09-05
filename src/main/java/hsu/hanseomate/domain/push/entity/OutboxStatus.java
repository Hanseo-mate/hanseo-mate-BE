package hsu.hanseomate.domain.push.entity;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    EXPIRED
}
