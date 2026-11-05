package com.tlavu.moodly.modules.entries.service.implementation;

import com.tlavu.moodly.modules.entries.exception.DailyEntryPersistenceException;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.HabitLog;
import com.tlavu.moodly.modules.entries.model.Mood;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.service.implementation.OwnedHabitService;
import com.tlavu.moodly.modules.habits.exception.HabitException;
import com.tlavu.moodly.modules.entries.service.input.SetMoodInput;
import com.tlavu.moodly.modules.entries.service.input.UpdateHabitLogInput;
import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;

@Service
@RequiredArgsConstructor
public class DailyEntryService {
	private final DailyEntryRepository dailyEntryRepository;
	private final OwnedHabitService ownedHabitService;
	private final MongoTemplate mongoTemplate;

	public DailyEntryView updateHabitLog(
			String userId,
			LocalDate date,
			UpdateHabitLogInput input
	) {
		Habit habit = ownedHabitService.require(userId, input.habitId());

		if (
				!habit.isActive() &&
				!dailyEntryRepository.findByUserIdAndDate(userId, date)
						.map(existing -> existing.getHabits().stream()
								.anyMatch(log -> input.habitId().equals(log.getHabitId()))
						).orElse(false)
		) {
			throw HabitException.archived();
		}

		DailyEntry entry = ensureEntry(userId, date);
		Query existing = Query.query(
				Criteria.where("_id")
						.is(entry.getId())
						.and("habits.habitId")
						.is(input.habitId())
		);
		Update replace = new Update().set("habits.$.done", input.done())
				.set("habits.$.note", input.note())
				.set("updatedAt", Instant.now());

		if (mongoTemplate.updateFirst(existing, replace, DailyEntry.class).getMatchedCount() == 0) {
			Query missing = Query.query(
					Criteria.where("_id")
							.is(entry.getId())
							.and("habits.habitId")
							.ne(input.habitId()));
			Update append = new Update().push(
					"habits",
					new HabitLog(
							input.habitId(),
							input.done(),
							input.note()
					)
			).set("updatedAt", Instant.now());

			if (
					mongoTemplate.updateFirst(missing, append, DailyEntry.class)
							.getMatchedCount() == 0
			) {
				mongoTemplate.updateFirst(existing, replace, DailyEntry.class);
			}
		}

		return DailyEntryView.from(dailyEntryRepository.findById(entry.getId())
				.orElseThrow(() -> new DailyEntryPersistenceException("Daily entry disappeared after habit update")));
	}

	public DailyEntryView setMood(
			String userId,
			LocalDate date,
			SetMoodInput input
	) {
		DailyEntry entry = ensureEntry(userId, date);
		DailyEntry updated = mongoTemplate.findAndModify(
				Query.query(Criteria.where("_id").is(entry.getId())),
				new Update().set(
						"mood",
						new Mood(
								input.score(),
								input.tags(),
								input.note()
						)
				).set("updatedAt", Instant.now()),
				FindAndModifyOptions.options().returnNew(true),
				DailyEntry.class
		);

		return DailyEntryView.from(
				Optional.ofNullable(updated)
						.orElseThrow(() -> new DailyEntryPersistenceException(
								"Daily entry disappeared during mood update"))
		);
	}

	public List<DailyEntryView> findBetween(
			String userId,
			LocalDate from,
			LocalDate to
	) {
		return dailyEntryRepository.findByUserIdAndDateRange(userId, from, to)
				.stream()
				.map(DailyEntryView::from)
				.toList();
	}

	public Page<DailyEntryView> findBetween(
			String userId,
			LocalDate from,
			LocalDate to,
			int page,
			int size
	) {
		PageRequest pageRequest = PageRequest.of(
				page,
				size,
				Sort.by(Sort.Direction.DESC, "date")
		);

		return dailyEntryRepository.findPageByUserIdAndDateRange(userId, from, to, pageRequest)
				.map(DailyEntryView::from);
	}

	public Optional<DailyEntryView> findByDate(String userId, LocalDate date) {
		return dailyEntryRepository.findByUserIdAndDate(userId, date)
				.map(DailyEntryView::from);
	}

	private DailyEntry ensureEntry(String userId, LocalDate date) {
		Instant now = Instant.now();

		try {
			return Optional.ofNullable(mongoTemplate.findAndModify(
					Query.query(
							Criteria.where("userId")
									.is(userId)
									.and("date")
									.is(date)
					),
					new Update().setOnInsert("userId", userId)
							.setOnInsert("date", date)
							.setOnInsert("habits", List.of())
							.setOnInsert("createdAt", now)
							.setOnInsert("updatedAt", now),
					FindAndModifyOptions.options()
							.upsert(true)
							.returnNew(true),
					DailyEntry.class
			)).orElseThrow(() -> new DailyEntryPersistenceException("Daily entry was not returned after upsert"));
		} catch (DuplicateKeyException race) {
			return dailyEntryRepository.findByUserIdAndDate(userId, date)
					.orElseThrow(() -> new DailyEntryPersistenceException(
							"Daily entry collided without an existing entry for the same user and date",
							race
					));
		}
	}
}
