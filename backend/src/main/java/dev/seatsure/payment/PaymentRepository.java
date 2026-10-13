package dev.seatsure.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select p from Payment p join fetch p.booking b join fetch b.event where p.idempotencyKey = :key")
    Optional<Payment> findByIdempotencyKey(String key);

    List<Payment> findByBookingIdOrderByCreatedAtAsc(Long bookingId);

    long countByBookingEventIdAndStatus(Long eventId, PaymentStatus status);
}
