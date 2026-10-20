package com.lin.spring.webhook.queue;

import com.lin.spring.webhook.dto.DeliveryJob;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存队列实现，供 local profile（免 Docker）与单元测试使用。
 */
@Component
@Profile("local")
public class InMemoryDeliveryQueue implements DeliveryQueue {

	private final BlockingQueue<DeliveryJob> ready = new LinkedBlockingQueue<>();
	private final ConcurrentSkipListMap<DelayedKey, DeliveryJob> delayed = new ConcurrentSkipListMap<>();
	private final AtomicLong sequence = new AtomicLong();

	@Override
	public void pushReady(DeliveryJob job) {
		ready.offer(job);
	}

	@Override
	public void pushDelayed(DeliveryJob job, long delaySeconds) {
		delayed.put(new DelayedKey(Instant.now().plusSeconds(delaySeconds), sequence.incrementAndGet()), job);
	}

	@Override
	public List<DeliveryJob> popReady(int maxBatch, Duration timeout) {
		List<DeliveryJob> jobs = new ArrayList<>();
		try {
			DeliveryJob first = ready.poll(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
			if (first == null) {
				return jobs;
			}
			jobs.add(first);
			while (jobs.size() < maxBatch) {
				DeliveryJob next = ready.poll();
				if (next == null) {
					break;
				}
				jobs.add(next);
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		return jobs;
	}

	@Override
	public List<DeliveryJob> drainDue(Instant now, int maxBatch) {
		List<DeliveryJob> jobs = new ArrayList<>();
		while (jobs.size() < maxBatch) {
			Map.Entry<DelayedKey, DeliveryJob> entry = delayed.firstEntry();
			if (entry == null || entry.getKey().dueAt().isAfter(now)) {
				break;
			}
			delayed.remove(entry.getKey());
			jobs.add(entry.getValue());
		}
		return jobs;
	}

	@Override
	public long readyDepth() {
		return ready.size();
	}

	@Override
	public long delayedDepth() {
		return delayed.size();
	}

	private record DelayedKey(Instant dueAt, long seq) implements Comparable<DelayedKey> {
		@Override
		public int compareTo(DelayedKey other) {
			int byTime = dueAt.compareTo(other.dueAt);
			return byTime != 0 ? byTime : Long.compare(seq, other.seq);
		}
	}
}
