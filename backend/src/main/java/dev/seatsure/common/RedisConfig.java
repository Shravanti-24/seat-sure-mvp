package dev.seatsure.common;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * All-or-nothing seat hold. Runs server side so that a batch of seats is claimed without any
     * window in which another request could interleave.
     */
    @Bean
    @SuppressWarnings("unchecked")
    public RedisScript<List> acquireHoldsScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/acquire_holds.lua"));
        script.setResultType(List.class);
        return script;
    }

    /** Releases only the hold keys that still belong to the given user. */
    @Bean
    public RedisScript<Long> releaseHoldsScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/release_holds.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
