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

package org.springframework.core.io;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * 资源类默认类加载器
 * Default implementation of the {@link ResourceLoader} interface.
 *
 * <p>Used by {@link ResourceEditor}, and serves as base class for
 * {@link org.springframework.context.support.AbstractApplicationContext}.
 * Can also be used standalone.
 *
 * <p>Will return a {@link UrlResource} if the location value is a URL,
 * and a {@link ClassPathResource} if it is a non-URL path or a
 * "classpath:" pseudo-URL.
 *
 * @author Juergen Hoeller
 * @see FileSystemResourceLoader
 * @see org.springframework.context.support.ClassPathXmlApplicationContext
 * @since 10.03.2004
 */
public class DefaultResourceLoader implements ResourceLoader {

	//类加载器
	@Nullable
	private ClassLoader classLoader;

	//协议解析器集合，ProtocolResolver 定义了从字符串到Resource的解析函数，ProtocolResolver 是一个接口。
	private final Set<ProtocolResolver> protocolResolvers = new LinkedHashSet<>(4);

	//资源缓存
	private final Map<Class<?>, Map<Resource, ?>> resourceCaches = new ConcurrentHashMap<>(4);


	/**
	 * Create a new DefaultResourceLoader.
	 * <p>ClassLoader access will happen using the thread context class loader
	 * at the time of actual resource access (since 5.3). For more control, pass
	 * a specific ClassLoader to {@link #DefaultResourceLoader(ClassLoader)}.
	 *
	 * @see java.lang.Thread#getContextClassLoader()
	 */
	public DefaultResourceLoader() {
	}

	/**
	 * Create a new DefaultResourceLoader.
	 *
	 * @param classLoader the ClassLoader to load class path resources with, or {@code null}
	 *                    for using the thread context class loader at the time of actual resource access
	 */
	public DefaultResourceLoader(@Nullable ClassLoader classLoader) {
		this.classLoader = classLoader;
	}


	/**
	 * Specify the ClassLoader to load class path resources with, or {@code null}
	 * for using the thread context class loader at the time of actual resource access.
	 * <p>The default is that ClassLoader access will happen using the thread context
	 * class loader at the time of actual resource access (since 5.3).
	 */
	public void setClassLoader(@Nullable ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	/**
	 * Return the ClassLoader to load class path resources with.
	 * <p>Will get passed to ClassPathResource's constructor for all
	 * ClassPathResource objects created by this resource loader.
	 *
	 * @see ClassPathResource
	 */
	@Override
	@Nullable
	public ClassLoader getClassLoader() {
		return (this.classLoader != null ? this.classLoader : ClassUtils.getDefaultClassLoader());
	}

	/**
	 * Register the given resolver with this resource loader, allowing for
	 * additional protocols to be handled.
	 * <p>Any such resolver will be invoked ahead of this loader's standard
	 * resolution rules. It may therefore also override any default rules.
	 *
	 * @see #getProtocolResolvers()
	 * @since 4.3
	 */
	public void addProtocolResolver(ProtocolResolver resolver) {
		Assert.notNull(resolver, "ProtocolResolver must not be null");
		this.protocolResolvers.add(resolver);
	}

	/**
	 * Return the collection of currently registered protocol resolvers,
	 * allowing for introspection as well as modification.
	 *
	 * @see #addProtocolResolver(ProtocolResolver)
	 * @since 4.3
	 */
	public Collection<ProtocolResolver> getProtocolResolvers() {
		return this.protocolResolvers;
	}

	/**
	 * Obtain a cache for the given value type, keyed by {@link Resource}.
	 *
	 * @param valueType the value type, e.g. an ASM {@code MetadataReader}
	 * @return the cache {@link Map}, shared at the {@code ResourceLoader} level
	 * @since 5.0
	 */
	@SuppressWarnings("unchecked")
	public <T> Map<Resource, T> getResourceCache(Class<T> valueType) {
		return (Map<Resource, T>) this.resourceCaches.computeIfAbsent(valueType, key -> new ConcurrentHashMap<>());
	}

	/**
	 * Clear all resource caches in this resource loader.
	 *
	 * @see #getResourceCache
	 * @since 5.0
	 */
	public void clearResourceCaches() {
		this.resourceCaches.clear();
	}

	/**
	 * 第一种：依靠协议解析接口(ProtocolResolver)进行处理
	 * <p>
	 * 第二种：待解析的资源路径是以 / 开头的处理，创建 ClassPathContextResource 对象返回
	 * <p>
	 * 第三种：待解析的资源路径是以 classpath: 开头的处理，创建 ClassPathResource 对象返回
	 * <p>
	 * 第四种：尝试创建 URL 对象
	 * <p>
	 * 创建成功：如果 URL 对象是文件类型的就返回 FileUrlResource 对象，否则就返回 UrlResource 对象
	 * <p>
	 * 创建失败：返回 ClassPathContextResource 对象
	 *
	 * @param location the resource location
	 * @return
	 */
	@Override
	public Resource getResource(String location) {
		Assert.notNull(location, "Location must not be null");
		// 获取协议解析器列表 循环
		for (ProtocolResolver protocolResolver : getProtocolResolvers()) {
			Resource resource = protocolResolver.resolve(location, this);
			if (resource != null) {
				return resource;
			}
		}
		// 路径地址是 / 开头
		if (location.startsWith("/")) {
			return getResourceByPath(location);
			// 如果 location 是类路径的方式，返回 ClassPathResource 类型的文件资源对象
		} else if (location.startsWith(CLASSPATH_URL_PREFIX)) {
			return new ClassPathResource(location.substring(CLASSPATH_URL_PREFIX.length()), getClassLoader());
		} else {
			try {
				// 如果是 URL 方式，返回 UrlResource 类型的文件资源对象，
				// 否则将抛出的异常进入 catch 代码块，返回另一种资源对象
				// Try to parse the location as a URL...
				URL url = ResourceUtils.toURL(location);
				return (ResourceUtils.isFileURL(url) ? new FileUrlResource(url) : new UrlResource(url));
			} catch (MalformedURLException ex) {
				// No URL -> resolve as resource path.
				// 如果既不是 classpath 标识，又不是 URL 标识的 Resource 定位，则调用容器本身的
				// getResourceByPath() 方法获取 Resource。根据实例化的子类对象，调用其子类对象中
				// 重写的此方法，如 FileSystemXmlApplicationContext 子类中对此方法的重写
				return getResourceByPath(location);
			}
		}
	}

	/**
	 * 实例化一个 FileSystemResource 并返回，以便后续对资源的 IO 操作本方法是在 DefaultResourceLoader 的 getResource() 方法中被调用的，
	 * Return a Resource handle for the resource at the given path.
	 * <p>The default implementation supports class path locations. This should
	 * be appropriate for standalone implementations but can be overridden,
	 * e.g. for implementations targeted at a Servlet container.
	 *
	 * @param path the path to the resource
	 * @return the corresponding Resource handle
	 * @see ClassPathResource
	 * @see org.springframework.context.support.FileSystemXmlApplicationContext#getResourceByPath
	 * @see org.springframework.web.context.support.XmlWebApplicationContext#getResourceByPath
	 */
	protected Resource getResourceByPath(String path) {
		return new ClassPathContextResource(path, getClassLoader());
	}


	/**
	 * ClassPathResource that explicitly expresses a context-relative path
	 * through implementing the ContextResource interface.
	 */
	protected static class ClassPathContextResource extends ClassPathResource implements ContextResource {

		public ClassPathContextResource(String path, @Nullable ClassLoader classLoader) {
			super(path, classLoader);
		}

		@Override
		public String getPathWithinContext() {
			return getPath();
		}

		@Override
		public Resource createRelative(String relativePath) {
			String pathToUse = StringUtils.applyRelativePath(getPath(), relativePath);
			return new ClassPathContextResource(pathToUse, getClassLoader());
		}
	}

}
