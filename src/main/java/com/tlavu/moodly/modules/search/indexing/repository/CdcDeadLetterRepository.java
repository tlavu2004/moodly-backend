package com.tlavu.moodly.modules.search.indexing.repository;

import com.tlavu.moodly.modules.search.indexing.model.CdcDeadLetter;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CdcDeadLetterRepository extends MongoRepository<CdcDeadLetter, String> {
}
