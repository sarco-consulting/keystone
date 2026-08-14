package com.keystone.orders.persistence;

import com.keystone.orders.domain.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // lineItems is LAZY by default (correct default — most queries over
    // orders don't need it); this fetch-joins it explicitly for the one
    // read path that does, to avoid a LazyInitializationException with
    // open-in-view disabled without resorting to OSIV.
    @Query("select o from Order o left join fetch o.lineItems where o.id = :id")
    Optional<Order> findByIdWithLineItems(@Param("id") UUID id);
}
