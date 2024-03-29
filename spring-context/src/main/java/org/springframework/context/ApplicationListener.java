/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context;

import java.util.EventListener;
import java.util.function.Consumer;

/**
 * 事件监听器
 * Interface to be implemented by application event listeners.
 *
 * <p>Based on the standard {@link java.util.EventListener} interface for the
 * Observer design pattern.
 *
 * <p>An {@code ApplicationListener} can generically declare the event type that
 * it is interested in. When registered with a Spring {@code ApplicationContext},
 * events will be filtered accordingly, with the listener getting invoked for
 * matching event objects only.
 *
 * @param <E> the specific {@code ApplicationEvent} subclass to listen to
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see org.springframework.context.ApplicationEvent
 * @see org.springframework.context.event.ApplicationEventMulticaster
 * @see org.springframework.context.event.SmartApplicationListener
 * @see org.springframework.context.event.GenericApplicationListener
 * @see org.springframework.context.event.EventListener
 */
@FunctionalInterface
public interface ApplicationListener<E extends ApplicationEvent> extends EventListener {

	/**
	 * 处理应用事件
	 * Handle an application event.
	 *
	 * @param event the event to respond to
	 */
	void onApplicationEvent(E event);


	/**
	 * Create a new {@code ApplicationListener} for the given payload consumer.
	 *
	 * @param consumer the event payload consumer
	 * @param <T>      the type of the event payload
	 * @return a corresponding {@code ApplicationListener} instance
	 * @see PayloadApplicationEvent
	 * @since 5.3
	 */
	static <T> ApplicationListener<PayloadApplicationEvent<T>> forPayload(Consumer<T> consumer) {
		return event -> consumer.accept(event.getPayload());
	}

}
