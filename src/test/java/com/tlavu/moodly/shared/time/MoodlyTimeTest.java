package com.tlavu.moodly.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MoodlyTimeTest {

	@Test
	void changesTheBusinessDateAtHoChiMinhMidnight() {
		Clock beforeMidnight = Clock.fixed(Instant.parse("2026-09-21T16:59:59Z"), ZoneOffset.UTC);
		Clock atMidnight = Clock.fixed(Instant.parse("2026-09-21T17:00:00Z"), ZoneOffset.UTC);

		assertThat(MoodlyTime.today(beforeMidnight)).isEqualTo(LocalDate.of(2026, 9, 21));
		assertThat(MoodlyTime.today(atMidnight)).isEqualTo(LocalDate.of(2026, 9, 22));
	}

	@Test
	void businessDateAndLogTimestampDoNotDependOnTheHostTimezone() throws Exception {
		Instant instant = Instant.parse("2026-09-21T17:00:00Z");
		assertThat(MoodlyTime.today(Clock.fixed(instant, java.time.ZoneId.of("America/Los_Angeles"))))
				.isEqualTo(LocalDate.of(2026, 9, 22));
		var context = new ch.qos.logback.classic.LoggerContext();
		try {
			var pattern = new ch.qos.logback.classic.PatternLayout();
			pattern.setContext(context);
			var xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
					.parse(new java.io.File("src/main/resources/logback-spring.xml"));
			var properties = xml.getElementsByTagName("property");
			String configured = ((org.w3c.dom.Element) properties.item(0)).getAttribute("value");
			pattern.setPattern(configured);
			pattern.start();
			var event = new ch.qos.logback.classic.spi.LoggingEvent();
			event.setTimeStamp(instant.toEpochMilli());
			event.setLevel(ch.qos.logback.classic.Level.INFO);
			event.setLoggerName("timezone-test");
			event.setMessage("timezone probe");
			event.setMDCPropertyMap(java.util.Map.of());
			assertThat(pattern.doLayout(event)).startsWith("2026-09-22T00:00:00.000+07:00");
		} finally { context.stop(); }
	}

	@Test
	void usesAZoneWithoutDaylightSavingTransitions() {
		assertThat(MoodlyTime.ZONE.getRules().nextTransition(Instant.parse("2026-01-01T00:00:00Z"))).isNull();
	}
}
