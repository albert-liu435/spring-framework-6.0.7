/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.core.env;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.lang.Nullable;

/**
 * 持有并保存多个PropertySource
 * <p>
 * PropertySources，从名字可以看出其包含多个 PropertySource。默认提供了一个 MutablePropertySources 实现，可以调用 addFirst 添加到列表的开头，addLast 添加到末尾，
 * 另外可以通过 addBefore(propertySourceName, propertySource) 或 addAfter(propertySourceName, propertySource) 添加到某个 propertySource 前面/后面；最后大家可以通过 iterator 迭代它，然后按照顺序获取属性。
 * <p>
 * Holder containing one or more {@link PropertySource} objects.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @see PropertySource
 * @since 3.1
 */
public interface PropertySources extends Iterable<PropertySource<?>> {

	/**
	 * Return a sequential {@link Stream} containing the property sources.
	 *
	 * @since 5.1
	 */
	default Stream<PropertySource<?>> stream() {
		return StreamSupport.stream(spliterator(), false);
	}

	/**
	 * 是否包含某个name的PropertySource
	 * Return whether a property source with the given name is contained.
	 *
	 * @param name the {@linkplain PropertySource#getName() name of the property source} to find
	 */
	boolean contains(String name);

	/**
	 * 根据name找到PropertySource
	 * Return the property source with the given name, {@code null} if not found.
	 *
	 * @param name the {@linkplain PropertySource#getName() name of the property source} to find
	 */
	@Nullable
	PropertySource<?> get(String name);

}
