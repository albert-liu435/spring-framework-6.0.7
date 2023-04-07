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

package org.springframework.context;

import org.springframework.beans.factory.Aware;
import org.springframework.util.StringValueResolver;

/**
 * 用于获取一个String类型的值解析器，解析容器中占位符中的字符串值，例如：${name}，#{12+10}等。第一个${name}为普通的占位符，容器中必须存在这个name属性，第二个#{12+10}为SPEL表达式，可以实现一些计算逻辑，通过注入的值解析器都可以完成解析。
 * Interface to be implemented by any object that wishes to be notified of a
 * {@code StringValueResolver} for the resolution of embedded definition values.
 *
 * <p>This is an alternative to a full ConfigurableBeanFactory dependency via the
 * {@code ApplicationContextAware}/{@code BeanFactoryAware} interfaces.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#resolveEmbeddedValue(String)
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#getBeanExpressionResolver()
 * @see org.springframework.beans.factory.config.EmbeddedValueResolver
 * @since 3.0.3
 */
public interface EmbeddedValueResolverAware extends Aware {

	/**
	 * Set the StringValueResolver to use for resolving embedded definition values.
	 */
	void setEmbeddedValueResolver(StringValueResolver resolver);

}
