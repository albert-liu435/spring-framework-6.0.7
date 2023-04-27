/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.beans.factory.config;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.io.support.PropertiesLoaderSupport;
import org.springframework.util.ObjectUtils;

/**
 * 允许从属性资源（即属性文件）配置单个bean属性值。对于覆盖在应用程序上下文中配置的bean属性的针对系统管理员的自定义配置文件非常有用。
 * <p>
 * Allows for configuration of individual bean property values from a property resource,
 * i.e. a properties file. Useful for custom config files targeted at system
 * administrators that override bean properties configured in the application context.
 * <p>
 * 发行版中提供了两个具体实现：
 * <p>
 * “beanName.property=value”样式重写的PropertyOverrideConfigurer（将属性文件中的值推送到bean定义中）
 * <p>
 * 用于替换“${…}”占位符的PropertyPlaceHolderConfigure（将属性文件中的值拉入bean定义）
 * ————————————————
 * 版权声明：本文为CSDN博主「敲代码的小小酥」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
 * 原文链接：https://blog.csdn.net/qq1309664161/article/details/118881496
 *
 * <p>Two concrete implementations are provided in the distribution:
 * <ul>
 * <li>{@link PropertyOverrideConfigurer} for "beanName.property=value" style overriding
 * (<i>pushing</i> values from a properties file into bean definitions)
 * <li>{@link PropertyPlaceholderConfigurer} for replacing "${...}" placeholders
 * (<i>pulling</i> values from a properties file into bean definitions)
 * </ul>
 * <p>
 * 在读入属性值后，可以通过重写convertPropertyValue方法来转换属性值。例如，可以在处理加密值之前相应地检测并解密它们。
 *
 * <p>Property values can be converted after reading them in, through overriding
 * the {@link #convertPropertyValue} method. For example, encrypted values
 * can be detected and decrypted accordingly before processing them.
 * <p>
 * <p>
 * <p>
 * 由上面的注释可知，这个类主要由两个子类PropertyOverrideConfigurer和PropertyPlaceHolderConfigure，主要是将配置文件中的属性赋值给Spring容器中的bean中。我们连接数据库的配置信息，包括${}替换properties中的属性，都是通过这个类及其子类实现的，下面我们看源码研究其实现原理。
 * <p>
 * 首先可以看到，他是一个抽象类，继承了PropertiesLoaderSupport类，实现了BeanFactoryPostProcessor和PriorityOrdered接口。
 * <p>
 * 这里我们先知道PropertyResourceConfigurer类实现了BeanFactoryPostProcessor类，即在形成BeanDefiniton，实例化之前，将properties中的属性进行了替换。
 * <p>
 * 可以看出这几个类都是实现了排序和优先级接口的，所以，它的运行流程就是先解析properties中的key和value。将其存入内存，然后再解析xml中配置的， 将 p r o p e r t i e s 解 析 的 值 ， 替 换 到 {}，将properties解析的值，替换到，将properties解析的值，替换到{}中去。这几个类就做了这点儿事情。所以我们在配置数据库连接时好用这种配置，其原理就出自于此
 * ————————————————
 * 版权声明：本文为CSDN博主「敲代码的小小酥」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
 * 原文链接：https://blog.csdn.net/qq1309664161/article/details/118881496
 *
 *
 * <context:property-placeholder location="classpath:config.properties"/>
 *
 * @author Juergen Hoeller
 * @see PropertyOverrideConfigurer
 * @see PropertyPlaceholderConfigurer
 * @since 02.10.2003
 */
public abstract class PropertyResourceConfigurer extends PropertiesLoaderSupport
		implements BeanFactoryPostProcessor, PriorityOrdered {

	private int order = Ordered.LOWEST_PRECEDENCE;  // default: same as non-Ordered


	/**
	 * Set the order value of this object for sorting purposes.
	 *
	 * @see PriorityOrdered
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}


	/**
	 * {@linkplain #mergeProperties Merge}, {@linkplain #convertProperties convert} and
	 * {@linkplain #processProperties process} properties against the given bean factory.
	 *
	 * @throws BeanInitializationException if any properties cannot be loaded
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		try {

			//1. 获取当前容器配置的所有Properties文件，可能由多个文件merge而来
			Properties mergedProps = mergeProperties();

			// Convert the merged properties, if necessary.
			//2. 如果需要的话，将Properties文件的内容进行转化，因为默认的Preperties都是String的key-value形式。
			//   Spring提供的默认方式是不转化，保持String,String的key-value
			convertProperties(mergedProps);

			// Let the subclass process the properties.
			//3. 由子类继承，对容器与Properties进行操作，即value注入
			processProperties(beanFactory, mergedProps);
		} catch (IOException ex) {
			throw new BeanInitializationException("Could not load properties: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Convert the given merged properties, converting property values
	 * if necessary. The result will then be processed.
	 * <p>The default implementation will invoke {@link #convertPropertyValue}
	 * for each property value, replacing the original with the converted value.
	 *
	 * @param props the Properties to convert
	 * @see #processProperties
	 */
	protected void convertProperties(Properties props) {
		Enumeration<?> propertyNames = props.propertyNames();
		while (propertyNames.hasMoreElements()) {
			String propertyName = (String) propertyNames.nextElement();
			String propertyValue = props.getProperty(propertyName);
			String convertedValue = convertProperty(propertyName, propertyValue);
			if (!ObjectUtils.nullSafeEquals(propertyValue, convertedValue)) {
				props.setProperty(propertyName, convertedValue);
			}
		}
	}

	/**
	 * Convert the given property from the properties source to the value
	 * which should be applied.
	 * <p>The default implementation calls {@link #convertPropertyValue(String)}.
	 *
	 * @param propertyName  the name of the property that the value is defined for
	 * @param propertyValue the original value from the properties source
	 * @return the converted value, to be used for processing
	 * @see #convertPropertyValue(String)
	 */
	protected String convertProperty(String propertyName, String propertyValue) {
		return convertPropertyValue(propertyValue);
	}

	/**
	 * Convert the given property value from the properties source to the value
	 * which should be applied.
	 * <p>The default implementation simply returns the original value.
	 * Can be overridden in subclasses, for example to detect
	 * encrypted values and decrypt them accordingly.
	 *
	 * @param originalValue the original value from the properties source
	 *                      (properties file or local "properties")
	 * @return the converted value, to be used for processing
	 * @see #setProperties
	 * @see #setLocations
	 * @see #setLocation
	 * @see #convertProperty(String, String)
	 */
	protected String convertPropertyValue(String originalValue) {
		return originalValue;
	}


	/**
	 * Apply the given Properties to the given BeanFactory.
	 *
	 * @param beanFactory the BeanFactory used by the application context
	 * @param props       the Properties to apply
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	protected abstract void processProperties(ConfigurableListableBeanFactory beanFactory, Properties props)
			throws BeansException;

}
