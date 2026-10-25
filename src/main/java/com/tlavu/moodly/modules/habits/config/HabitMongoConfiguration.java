package com.tlavu.moodly.modules.habits.config;

import com.tlavu.moodly.modules.habits.domain.TargetFrequency;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Configuration
public class HabitMongoConfiguration {

    @Bean
    MongoCustomConversions habitMongoConversions() {
        return MongoCustomConversions.create(adapter -> adapter.registerConverter(new TargetFrequencyReader()));
    }

    @ReadingConverter
    static class TargetFrequencyReader implements Converter<String, TargetFrequency> {
        @Override
        public TargetFrequency convert(String source) {
            // Only the known legacy value is accepted. Writes and API remain DAILY.
            if ("daily".equals(source)) return TargetFrequency.DAILY;
            return TargetFrequency.valueOf(source);
        }
    }
}
