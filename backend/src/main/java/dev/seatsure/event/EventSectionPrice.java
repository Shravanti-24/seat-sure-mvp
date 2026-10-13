package dev.seatsure.event;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/** Prices are integer paise. Money is never a floating point number in this codebase. */
@Entity
@Table(name = "event_section_price")
@Getter
@Setter
public class EventSectionPrice {

    @EmbeddedId
    private Key key;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    @Embeddable
    @Getter
    @Setter
    public static class Key implements Serializable {

        @Column(name = "event_id", nullable = false)
        private Long eventId;

        @Column(name = "section_id", nullable = false)
        private Long sectionId;

        public Key() {}

        public Key(Long eventId, Long sectionId) {
            this.eventId = eventId;
            this.sectionId = sectionId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(eventId, key.eventId) && Objects.equals(sectionId, key.sectionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, sectionId);
        }
    }
}
