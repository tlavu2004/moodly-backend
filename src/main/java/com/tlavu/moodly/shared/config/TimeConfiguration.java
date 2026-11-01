package com.tlavu.moodly.shared.config;

import com.tlavu.moodly.shared.time.MoodlyTime;
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
