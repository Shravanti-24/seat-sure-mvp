package dev.seatsure;

import dev.seatsure.common.SeatSureProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SeatSureProperties.class)
public class SeatSureApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeatSureApplication.class, args);
    }
}
