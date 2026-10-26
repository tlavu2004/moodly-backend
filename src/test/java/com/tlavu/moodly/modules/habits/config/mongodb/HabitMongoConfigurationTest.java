package com.tlavu.moodly.modules.habits.config.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tlavu.moodly.modules.habits.config.converter.TargetFrequencyReader;
import com.tlavu.moodly.modules.habits.domain.TargetFrequency;
import com.tlavu.moodly.modules.habits.model.Habit;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class HabitMongoConfigurationTest {
	@Test
	void readsLegacyHabitAndWritesCanonicalFrequency() throws Exception {
		MongoCustomConversions conversions = new HabitMongoConfiguration().habitMongoConversions();
		MongoMappingContext context = new MongoMappingContext();
		context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
		context.afterPropertiesSet();
		MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
		converter.setCustomConversions(conversions);
		converter.afterPropertiesSet();
		Habit habit = converter.read(Habit.class, new Document("targetFrequency", "daily"));
		assertThat(habit.getTargetFrequency()).isEqualTo(TargetFrequency.DAILY);
		Document output = new Document();
		converter.write(habit, output);
		assertThat(output.getString("targetFrequency")).isEqualTo("DAILY");
		assertThat(converter.read(Habit.class, new Document("targetFrequency", "DAILY")).getTargetFrequency())
				.isEqualTo(TargetFrequency.DAILY);
	}

	@Test
	void rejectsUnknownFrequencies() {
		TargetFrequencyReader reader = new TargetFrequencyReader();
		assertThatThrownBy(() -> reader.convert("weekly")).isInstanceOf(IllegalArgumentException.class);
	}
}
