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

package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.FatalBeanException;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * 基于映射文件中包含的映射，将命名空间URI解析为实现类。
 * Default implementation of the {@link NamespaceHandlerResolver} interface.
 * Resolves namespace URIs to implementation classes based on the mappings
 * contained in mapping file.
 *
 * <p>By default, this implementation looks for the mapping file at
 * {@code META-INF/spring.handlers}, but this can be changed using the
 * {@link #DefaultNamespaceHandlerResolver(ClassLoader, String)} constructor.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @see NamespaceHandler
 * @see DefaultBeanDefinitionDocumentReader
 * @since 2.0
 */
public class DefaultNamespaceHandlerResolver implements NamespaceHandlerResolver {

	/**
	 * The location to look for the mapping files. Can be present in multiple JAR files.
	 */
	public static final String DEFAULT_HANDLER_MAPPINGS_LOCATION = "META-INF/spring.handlers";


	/**
	 * Logger available to subclasses.
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * ClassLoader to use for NamespaceHandler classes.
	 */
	@Nullable
	private final ClassLoader classLoader;

	/**
	 * Resource location to search for.
	 */
	private final String handlerMappingsLocation;

	/**
	 * Stores the mappings from namespace URI to NamespaceHandler class name / instance.
	 */
	@Nullable
	private volatile Map<String, Object> handlerMappings;


	/**
	 * Create a new {@code DefaultNamespaceHandlerResolver} using the
	 * default mapping file location.
	 * <p>This constructor will result in the thread context ClassLoader being used
	 * to load resources.
	 *
	 * @see #DEFAULT_HANDLER_MAPPINGS_LOCATION
	 */
	public DefaultNamespaceHandlerResolver() {
		this(null, DEFAULT_HANDLER_MAPPINGS_LOCATION);
	}

	/**
	 * Create a new {@code DefaultNamespaceHandlerResolver} using the
	 * default mapping file location.
	 *
	 * @param classLoader the {@link ClassLoader} instance used to load mapping resources
	 *                    (may be {@code null}, in which case the thread context ClassLoader will be used)
	 * @see #DEFAULT_HANDLER_MAPPINGS_LOCATION
	 */
	public DefaultNamespaceHandlerResolver(@Nullable ClassLoader classLoader) {
		this(classLoader, DEFAULT_HANDLER_MAPPINGS_LOCATION);
	}

	/**
	 * Create a new {@code DefaultNamespaceHandlerResolver} using the
	 * supplied mapping file location.
	 *
	 * @param classLoader             the {@link ClassLoader} instance used to load mapping resources
	 *                                may be {@code null}, in which case the thread context ClassLoader will be used
	 * @param handlerMappingsLocation the mapping file location
	 */
	public DefaultNamespaceHandlerResolver(@Nullable ClassLoader classLoader, String handlerMappingsLocation) {
		Assert.notNull(handlerMappingsLocation, "Handler mappings location must not be null");
		this.classLoader = (classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader());
		this.handlerMappingsLocation = handlerMappingsLocation;
	}


	/**
	 * 获取命名空间映射关系即 命名空间url -> 命名空间解析对象类全路径 ， 是一个 map 集合
	 * <p>
	 * 在第一步中得到的 map 集合中根据参数 namespaceUri 获取对应的对象
	 * <p>
	 * 根据对象的不同情况进行处理
	 * <p>
	 * 情况1: 通过 namespaceUri 获取的对象是 NamespaceHandler 类型
	 * <p>
	 * 直接返回即可使用
	 * <p>
	 * 情况2: 通过 namespaceUri 获取的对象不是 NamespaceHandler 类型
	 * <p>
	 * 当获取的不是 NamespaceHandler 时只可能是字符串 ， 字符串是没有办法直接使用方法的 (NamespaceHandler 的方法 ) ， 在此 Spring 需要将其转换成 NamespaceHandler 对象
	 * <p>
	 * 在转换完成对象后进行 init 方法调用.
	 * <p>
	 * 最后将数据重新放入缓存. 此时需要注意放入缓存的 value 是一个具体的对象， 而不是字符串.
	 * <p>
	 * <p>
	 * Locate the {@link NamespaceHandler} for the supplied namespace URI
	 * from the configured mappings.
	 *
	 * @param namespaceUri the relevant namespace URI
	 * @return the located {@link NamespaceHandler}, or {@code null} if none found
	 */
	@Override
	@Nullable
	public NamespaceHandler resolve(String namespaceUri) {
		// 获取 namespace handler 映射表
		Map<String, Object> handlerMappings = getHandlerMappings();
		// 从映射表中获取 uri 对应的 handler
		// 1. 字符串(名称)
		// 2. 实例
		Object handlerOrClassName = handlerMappings.get(namespaceUri);
		if (handlerOrClassName == null) {
			return null;
		} else if (handlerOrClassName instanceof NamespaceHandler namespaceHandler) {
			return namespaceHandler;
		}
		// 其他情况都做字符串处理
		else {
			String className = (String) handlerOrClassName;
			try {
				Class<?> handlerClass = ClassUtils.forName(className, this.classLoader);
				if (!NamespaceHandler.class.isAssignableFrom(handlerClass)) {
					throw new FatalBeanException("Class [" + className + "] for namespace [" + namespaceUri +
							"] does not implement the [" + NamespaceHandler.class.getName() + "] interface");
				}
				// 通过反射构造 namespaceHandler 实例
				NamespaceHandler namespaceHandler = (NamespaceHandler) BeanUtils.instantiateClass(handlerClass);
				// 初始化
				namespaceHandler.init();
				// 重写缓存
				handlerMappings.put(namespaceUri, namespaceHandler);
				return namespaceHandler;
			} catch (ClassNotFoundException ex) {
				throw new FatalBeanException("Could not find NamespaceHandler class [" + className +
						"] for namespace [" + namespaceUri + "]", ex);
			} catch (LinkageError err) {
				throw new FatalBeanException("Unresolvable class definition for NamespaceHandler class [" +
						className + "] for namespace [" + namespaceUri + "]", err);
			}
		}
	}

	/**
	 * Load the specified NamespaceHandler mappings lazily.
	 */
	private Map<String, Object> getHandlerMappings() {
		// 设置容器
		Map<String, Object> handlerMappings = this.handlerMappings;
		if (handlerMappings == null) {
			synchronized (this) {
				handlerMappings = this.handlerMappings;
				if (handlerMappings == null) {
					if (logger.isTraceEnabled()) {
						logger.trace("Loading NamespaceHandler mappings from [" + this.handlerMappingsLocation + "]");
					}
					try {
						// 读取 资源文件地址
						Properties mappings =
								PropertiesLoaderUtils.loadAllProperties(this.handlerMappingsLocation, this.classLoader);
						if (logger.isTraceEnabled()) {
							logger.trace("Loaded NamespaceHandler mappings: " + mappings);
						}
						handlerMappings = new ConcurrentHashMap<>(mappings.size());
						// 数据合并， 将 mappings 数据拷贝给 handlerMappings
						CollectionUtils.mergePropertiesIntoMap(mappings, handlerMappings);
						this.handlerMappings = handlerMappings;
					} catch (IOException ex) {
						throw new IllegalStateException(
								"Unable to load NamespaceHandler mappings from location [" + this.handlerMappingsLocation + "]", ex);
					}
				}
			}
		}
		return handlerMappings;
	}


	@Override
	public String toString() {
		return "NamespaceHandlerResolver using mappings " + getHandlerMappings();
	}

}
