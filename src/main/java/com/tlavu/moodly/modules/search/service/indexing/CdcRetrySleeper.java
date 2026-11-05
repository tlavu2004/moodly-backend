package com.tlavu.moodly.modules.search.service.indexing;

import com.tlavu.moodly.shared.error.exception.common.OperationInterruptedException;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class CdcRetrySleeper {
	public void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new OperationInterruptedException("CDC retry wait interrupted", interrupted);
		}
	}
}
