package com.tlavu.moodly.modules.habits.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.model.TargetFrequency;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class HabitMongoConfigurationTest {
    @Test
    void readsLegacyHabitAndWritesCanonicalFrequency() throws Exception {
        var conversions = new HabitMongoConfiguration().habitMongoConversions();
        var context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        var habit = converter.read(Habit.class, new Document("targetFrequency", "daily"));
        assertThat(habit.getTargetFrequency()).isEqualTo(TargetFrequency.DAILY);
        var output = new Document();
        converter.write(habit, output);
        assertThat(output.getString("targetFrequency")).isEqualTo("DAILY");
        assertThat(converter.read(Habit.class, new Document("targetFrequency", "DAILY")).getTargetFrequency())
                .isEqualTo(TargetFrequency.DAILY);
    }

    @Test
    void rejectsUnknownFrequencies() {
        var reader = new HabitMongoConfiguration.TargetFrequencyReader();
        assertThatThrownBy(() -> reader.convert("weekly")).isInstanceOf(IllegalArgumentException.class);
    }
}
