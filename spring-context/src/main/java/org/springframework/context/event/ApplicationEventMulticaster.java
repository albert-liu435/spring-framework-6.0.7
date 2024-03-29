/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.context.event;

import java.util.function.Predicate;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * 功能: 该接口由可以管理多个ApplicationListener对象并向其发布事件的对象实现。一个 org.springframework.context.ApplicationEventPublisher通常是一个org.springframework.context.ApplicationContext,
 * 可以使用ApplicationEventMulticaster作为实际发布事件的委托。
 * 
 *ApplicationEventMulticaster接口的实现类可以管理多个ApplicationListener监听器对象，并且发布事件到监听器；ApplicationEventMulticaster其实是ApplicationEventPublisher发布事件的代理类，
 * 通常作为SpringApplicationRunListener接口实现类EventPublishingRunListener的一个属性来使用；
 *
 * <p>
 * Interface to be implemented by objects that can manage a number of
 * {@link ApplicationListener} objects and publish events to them.
 *
 * <p>An {@link org.springframework.context.ApplicationEventPublisher}, typically
 * a Spring {@link org.springframework.context.ApplicationContext}, can use an
 * {@code ApplicationEventMulticaster} as a delegate for actually publishing events.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @see ApplicationListener
 */
public interface ApplicationEventMulticaster {

	/**
	 * 添加一个侦听器以通知所有事件
	 * Add a listener to be notified of all events.
	 *
	 * @param listener the listener to add
	 * @see #removeApplicationListener(ApplicationListener)
	 * @see #removeApplicationListeners(Predicate)
	 */
	void addApplicationListener(ApplicationListener<?> listener);

	/**
	 * 添加一个侦听器bean以通知所有事件
	 * Add a listener bean to be notified of all events.
	 *
	 * @param listenerBeanName the name of the listener bean to add
	 * @see #removeApplicationListenerBean(String)
	 * @see #removeApplicationListenerBeans(Predicate)
	 */
	void addApplicationListenerBean(String listenerBeanName);

	/**
	 * 从通知列表中删除侦听器
	 * Remove a listener from the notification list.
	 *
	 * @param listener the listener to remove
	 * @see #addApplicationListener(ApplicationListener)
	 * @see #removeApplicationListeners(Predicate)
	 */
	void removeApplicationListener(ApplicationListener<?> listener);

	/**
	 * 从通知列表中删除侦听器bean
	 * Remove a listener bean from the notification list.
	 *
	 * @param listenerBeanName the name of the listener bean to remove
	 * @see #addApplicationListenerBean(String)
	 * @see #removeApplicationListenerBeans(Predicate)
	 */
	void removeApplicationListenerBean(String listenerBeanName);

	/**
	 * 从已注册的ApplicationListener实例集中删除所有匹配的侦听器（包括适配器类，如ApplicationListenerMethodAdapter，例如注释的EventListener方法）。
	 * 注意：这仅适用于实例注册，而不适用于按bean名称注册的侦听器。
	 * Remove all matching listeners from the set of registered
	 * {@code ApplicationListener} instances (which includes adapter classes
	 * such as {@link ApplicationListenerMethodAdapter}, e.g. for annotated
	 * {@link EventListener} methods).
	 * <p>Note: This just applies to instance registrations, not to listeners
	 * registered by bean name.
	 *
	 * @param predicate the predicate to identify listener instances to remove,
	 *                  e.g. checking {@link SmartApplicationListener#getListenerId()}
	 * @see #addApplicationListener(ApplicationListener)
	 * @see #removeApplicationListener(ApplicationListener)
	 * @since 5.3.5
	 */
	void removeApplicationListeners(Predicate<ApplicationListener<?>> predicate);

	/**
	 * 从注册的侦听器bean名称集中删除所有匹配的侦听器bean（引用bean类，这些bean类反过来直接实现ApplicationListener接口）。
	 * 注意：这仅适用于bean名称注册，而不适用于以编程方式注册的ApplicationListener实例。
	 * Remove all matching listener beans from the set of registered
	 * listener bean names (referring to bean classes which in turn
	 * implement the {@link ApplicationListener} interface directly).
	 * <p>Note: This just applies to bean name registrations, not to
	 * programmatically registered {@code ApplicationListener} instances.
	 *
	 * @param predicate the predicate to identify listener bean names to remove
	 * @see #addApplicationListenerBean(String)
	 * @see #removeApplicationListenerBean(String)
	 * @since 5.3.5
	 */
	void removeApplicationListenerBeans(Predicate<String> predicate);

	/**
	 * 删除所有注册到此multicaster的侦听器。
	 * 在删除调用之后，在注册新的侦听器之前，Multimaster不会对事件通知执行任何操作。
	 * Remove all listeners registered with this multicaster.
	 * <p>After a remove call, the multicaster will perform no action
	 * on event notification until new listeners are registered.
	 *
	 * @see #removeApplicationListeners(Predicate)
	 */
	void removeAllListeners();

	/**
	 * 将给定的应用程序事件多播到适当的侦听器。
	 * 如果可能，考虑使用multicastEvent（ApplicationEvent，ResolvableType），因为它为基于泛型的事件提供了更好的支持
	 * Multicast the given application event to appropriate listeners.
	 * <p>Consider using {@link #multicastEvent(ApplicationEvent, ResolvableType)}
	 * if possible as it provides better support for generics-based events.
	 *
	 * @param event the event to multicast
	 */
	void multicastEvent(ApplicationEvent event);

	/**
	 * 将给定的应用程序事件多播到适当的侦听器。
	 * 如果eventType为null，则基于事件实例生成默认类型
	 * Multicast the given application event to appropriate listeners.
	 * <p>If the {@code eventType} is {@code null}, a default type is built
	 * based on the {@code event} instance.
	 *
	 * @param event     the event to multicast
	 * @param eventType the type of event (can be {@code null})
	 * @since 4.2
	 */
	void multicastEvent(ApplicationEvent event, @Nullable ResolvableType eventType);

}
