package dev.seatsure.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSectionPriceRepository extends JpaRepository<EventSectionPrice, EventSectionPrice.Key> {

    List<EventSectionPrice> findByKeyEventId(Long eventId);
}
