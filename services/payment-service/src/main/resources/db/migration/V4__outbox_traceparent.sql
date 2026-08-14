-- Carries the W3C traceparent captured when the row was written, so the
-- relay (running on an unrelated scheduler thread) can restore the correct
-- trace before publishing — see TraceContextPropagation in common-events.
ALTER TABLE outbox_messages ADD COLUMN traceparent VARCHAR(64);
