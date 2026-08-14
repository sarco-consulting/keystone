package com.keystone.events.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * {@code @NoRepositoryBean}: this is a template for each service's concrete
 * repository (bound to its own {@code @Entity} subclass), not a repository
 * Spring Data should try to instantiate on its own.
 */
@NoRepositoryBean
public interface OutboxMessageRepository<T extends OutboxMessage> extends JpaRepository<T, UUID> {

    List<T> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
