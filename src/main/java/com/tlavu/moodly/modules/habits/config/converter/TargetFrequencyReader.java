package com.tlavu.moodly.modules.habits.config.converter;

import com.tlavu.moodly.modules.habits.domain.TargetFrequency;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class TargetFrequencyReader implements Converter<String, TargetFrequency> {
	@Override
	public TargetFrequency convert(@NonNull String source) {
		if (source.equals("daily")) {
			return TargetFrequency.DAILY;
		}

		return TargetFrequency.valueOf(source);
	}
}
