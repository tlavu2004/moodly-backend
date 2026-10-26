package com.tlavu.moodly.modules.habits.config.mongodb;

import com.tlavu.moodly.modules.habits.config.converter.TargetFrequencyReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Configuration
public class HabitMongoConfiguration {
    @Bean
    public MongoCustomConversions habitMongoConversions() {
        return MongoCustomConversions.create(
			adapter -> adapter.registerConverter(
					new TargetFrequencyReader()
			)
		);
    }
}
