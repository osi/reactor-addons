/*
 * Copyright (c) 2017 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.retry;

import java.time.Duration;
import java.util.function.Function;

/**
 * Backoff function
 *
 */
public interface Backoff extends Function<Context<?>, BackoffDelay> {

	public static final Backoff ZERO_BACKOFF = context -> BackoffDelay.ZERO;

	/**
	 * Backoff function with no backoff delay
	 * @return Backoff function for zero backoff delay
	 */
	static Backoff zero() {
		return ZERO_BACKOFF;
	}

	/**
	 * Backoff function with fixed backoff delay
	 * @param backoffInterval backoff interval
	 * @return Backoff function with fixed backoff delay
	 */
	static Backoff fixed(Duration backoffInterval) {
		return context -> new BackoffDelay(backoffInterval);
	}

	/**
	 * Backoff function with exponential backoff delay. Retries are performed after a backoff
	 * interval of <code>firstBackoff * (factor ** n)</code> where n is the iteration. If
	 * <code>maxBackoff</code> is not null, the maximum backoff applied will be limited to
	 * <code>maxBackoff</code>.
	 * <p>
	 * If <code>basedOnPreviousValue</code> is true, backoff will be calculated using
	 * <code>prevBackoff * factor</code>. When backoffs are combined with {@link Jitter}, this
	 * value will be different from the actual exponential value for the iteration.
	 *
	 * @param firstBackoff First backoff duration
	 * @param maxBackoff Maximum backoff duration
	 * @param factor The multiplicand for calculating backoff
	 * @param basedOnPreviousValue If true, calculation is based on previous value which may
	 *        be a backoff with jitter applied
	 * @return Backoff function with exponential delay
	 */
	static Backoff exponential(Duration firstBackoff, Duration maxBackoff, int factor, boolean basedOnPreviousValue) {
		if (firstBackoff == null || firstBackoff.isNegative() || firstBackoff.isZero())
			throw new IllegalArgumentException("firstBackoff must be > 0");
		Duration maxBackoffInterval = maxBackoff != null ? maxBackoff : Duration.ofSeconds(Long.MAX_VALUE);
		if (maxBackoffInterval.compareTo(firstBackoff) <= 0)
			throw new IllegalArgumentException("maxBackoff must be >= firstBackoff");
		if (!basedOnPreviousValue) {
			return context -> {
				Duration nextBackoff = firstBackoff.multipliedBy((long) Math.pow(factor, (context.iteration() - 1)));
				return new BackoffDelay(firstBackoff, maxBackoffInterval, nextBackoff);
			};
		}
		else {
			return context -> {
				Duration prevBackoff = context.backoff() == null ? Duration.ZERO : context.backoff();
				Duration nextBackoff = prevBackoff.multipliedBy(factor);
				nextBackoff = nextBackoff.compareTo(firstBackoff) < 0 ? firstBackoff : nextBackoff;
				return new BackoffDelay(firstBackoff, maxBackoff, nextBackoff);
			};
		}
	}
}
