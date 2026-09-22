package com.tlavu.moodly.shared.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {
	@Bean
	Clock moodlyClock() {
		return Clock.system(MoodlyTime.ZONE);
	}
}
