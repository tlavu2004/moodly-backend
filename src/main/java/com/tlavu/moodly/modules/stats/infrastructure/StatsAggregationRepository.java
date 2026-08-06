package com.tlavu.moodly.modules.stats.infrastructure;

import com.tlavu.moodly.modules.stats.api.MoodTrendResponse;
import com.tlavu.moodly.modules.stats.api.MostMissedHabitResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class StatsAggregationRepository {

	private static final ZoneId HO_CHI_MINH_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private final MongoTemplate mongoTemplate;
	private final String dailyEntriesCollection;

	public StatsAggregationRepository(
			MongoTemplate mongoTemplate,
			@Value("${moodly.entries.collection-name}") String dailyEntriesCollection
	) {
		this.mongoTemplate = mongoTemplate;
		this.dailyEntriesCollection = dailyEntriesCollection;
	}

	public List<MoodTrendResponse> findWeeklyMoodTrend(String userId) {
		Document weekStart = new Document("$dateTrunc", new Document("date", "$date")
				.append("unit", "week")
				.append("startOfWeek", "monday")
				.append("timezone", HO_CHI_MINH_ZONE.getId()));

		Document group = new Document("$group", new Document("_id", weekStart)
				.append("averageScore", new Document("$avg", "$mood.score"))
				.append("entryCount", new Document("$sum", 1)));

		Aggregation aggregation = Aggregation.newAggregation(
				Aggregation.match(Criteria.where("userId").is(userId).and("mood.score").exists(true)),
				Aggregation.stage(group.toJson()),
				Aggregation.sort(Sort.Direction.ASC, "_id")
		);

		AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, dailyEntriesCollection, Document.class);
		return results.getMappedResults().stream()
				.map(this::toMoodTrendResponse)
				.toList();
	}

	public List<MostMissedHabitResponse> findMostMissedHabits(String userId) {
		Aggregation aggregation = Aggregation.newAggregation(
				Aggregation.match(Criteria.where("userId").is(userId)),
				Aggregation.unwind("habits"),
				Aggregation.match(Criteria.where("habits.done").is(false)),
				Aggregation.group("habits.habitId").count().as("missedCount"),
				Aggregation.sort(Sort.Direction.DESC, "missedCount")
		);

		AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, dailyEntriesCollection, Document.class);
		return results.getMappedResults().stream()
				.map(this::toMostMissedHabitResponse)
				.toList();
	}

	private MoodTrendResponse toMoodTrendResponse(Document result) {
		return new MoodTrendResponse(
				toLocalDate(result.getDate("_id")),
				result.getDouble("averageScore"),
				((Number) result.get("entryCount")).longValue()
		);
	}

	private MostMissedHabitResponse toMostMissedHabitResponse(Document result) {
		return new MostMissedHabitResponse(
				result.getString("_id"),
				((Number) result.get("missedCount")).longValue()
		);
	}

	private LocalDate toLocalDate(Date date) {
		return date.toInstant().atZone(HO_CHI_MINH_ZONE).toLocalDate();
	}
}
