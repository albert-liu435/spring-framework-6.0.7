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

package org.springframework.context.annotation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.index.CandidateComponentsIndex;
import org.springframework.context.index.CandidateComponentsIndexLoader;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Indexed;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * A component provider that provides candidate components from a base package. Can
 * use {@link CandidateComponentsIndex the index} if it is available of scans the
 * classpath otherwise. Candidate components are identified by applying exclude and
 * include filters. {@link AnnotationTypeFilter}, {@link AssignableTypeFilter} include
 * filters on an annotation/superclass that are annotated with {@link Indexed} are
 * supported: if any other include filter is specified, the index is ignored and
 * classpath scanning is used instead.
 *
 * <p>This implementation is based on Spring's
 * {@link org.springframework.core.type.classreading.MetadataReader MetadataReader}
 * facility, backed by an ASM {@link org.springframework.asm.ClassReader ClassReader}.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Chris Beams
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @see org.springframework.core.type.classreading.MetadataReaderFactory
 * @see org.springframework.core.type.AnnotationMetadata
 * @see ScannedGenericBeanDefinition
 * @see CandidateComponentsIndex
 * @since 2.5
 */
public class ClassPathScanningCandidateComponentProvider implements EnvironmentCapable, ResourceLoaderAware {

	static final String DEFAULT_RESOURCE_PATTERN = "**/*.class";


	protected final Log logger = LogFactory.getLog(getClass());

	private String resourcePattern = DEFAULT_RESOURCE_PATTERN;

	private final List<TypeFilter> includeFilters = new ArrayList<>();

	private final List<TypeFilter> excludeFilters = new ArrayList<>();

	@Nullable
	private Environment environment;

	@Nullable
	private ConditionEvaluator conditionEvaluator;

	@Nullable
	private ResourcePatternResolver resourcePatternResolver;

	@Nullable
	private MetadataReaderFactory metadataReaderFactory;

	@Nullable
	private CandidateComponentsIndex componentsIndex;


	/**
	 * Protected constructor for flexible subclass initialization.
	 *
	 * @since 4.3.6
	 */
	protected ClassPathScanningCandidateComponentProvider() {
	}

	/**
	 * Create a ClassPathScanningCandidateComponentProvider with a {@link StandardEnvironment}.
	 *
	 * @param useDefaultFilters whether to register the default filters for the
	 *                          {@link Component @Component}, {@link Repository @Repository},
	 *                          {@link Service @Service}, and {@link Controller @Controller}
	 *                          stereotype annotations
	 * @see #registerDefaultFilters()
	 */
	public ClassPathScanningCandidateComponentProvider(boolean useDefaultFilters) {
		this(useDefaultFilters, new StandardEnvironment());
	}

	/**
	 * Create a ClassPathScanningCandidateComponentProvider with the given {@link Environment}.
	 *
	 * @param useDefaultFilters whether to register the default filters for the
	 *                          {@link Component @Component}, {@link Repository @Repository},
	 *                          {@link Service @Service}, and {@link Controller @Controller}
	 *                          stereotype annotations
	 * @param environment       the Environment to use
	 * @see #registerDefaultFilters()
	 */
	public ClassPathScanningCandidateComponentProvider(boolean useDefaultFilters, Environment environment) {
		if (useDefaultFilters) {
			registerDefaultFilters();
		}
		setEnvironment(environment);
		setResourceLoader(null);
	}


	/**
	 * Set the resource pattern to use when scanning the classpath.
	 * This value will be appended to each base package name.
	 *
	 * @see #findCandidateComponents(String)
	 * @see #DEFAULT_RESOURCE_PATTERN
	 */
	public void setResourcePattern(String resourcePattern) {
		Assert.notNull(resourcePattern, "'resourcePattern' must not be null");
		this.resourcePattern = resourcePattern;
	}

	/**
	 * Add an include type filter to the <i>end</i> of the inclusion list.
	 */
	public void addIncludeFilter(TypeFilter includeFilter) {
		this.includeFilters.add(includeFilter);
	}

	/**
	 * Add an exclude type filter to the <i>front</i> of the exclusion list.
	 */
	public void addExcludeFilter(TypeFilter excludeFilter) {
		this.excludeFilters.add(0, excludeFilter);
	}

	/**
	 * Reset the configured type filters.
	 *
	 * @param useDefaultFilters whether to re-register the default filters for
	 *                          the {@link Component @Component}, {@link Repository @Repository},
	 *                          {@link Service @Service}, and {@link Controller @Controller}
	 *                          stereotype annotations
	 * @see #registerDefaultFilters()
	 */
	public void resetFilters(boolean useDefaultFilters) {
		this.includeFilters.clear();
		this.excludeFilters.clear();
		if (useDefaultFilters) {
			registerDefaultFilters();
		}
	}

	/**
	 * Register the default filter for {@link Component @Component}.
	 * <p>This will implicitly register all annotations that have the
	 * {@link Component @Component} meta-annotation including the
	 * {@link Repository @Repository}, {@link Service @Service}, and
	 * {@link Controller @Controller} stereotype annotations.
	 * <p>Also supports Jakarta EE's {@link jakarta.annotation.ManagedBean} and
	 * JSR-330's {@link jakarta.inject.Named} annotations, if available.
	 */
	@SuppressWarnings("unchecked")
	protected void registerDefaultFilters() {
		// 注册@Component对应的AnnotationTypeFilter
		this.includeFilters.add(new AnnotationTypeFilter(Component.class));
		ClassLoader cl = ClassPathScanningCandidateComponentProvider.class.getClassLoader();
		try {
			this.includeFilters.add(new AnnotationTypeFilter(
					((Class<? extends Annotation>) ClassUtils.forName("jakarta.annotation.ManagedBean", cl)), false));
			logger.trace("JSR-250 'jakarta.annotation.ManagedBean' found and supported for component scanning");
		} catch (ClassNotFoundException ex) {
			// JSR-250 1.1 API (as included in Jakarta EE) not available - simply skip.
		}
		try {
			this.includeFilters.add(new AnnotationTypeFilter(
					((Class<? extends Annotation>) ClassUtils.forName("jakarta.inject.Named", cl)), false));
			logger.trace("JSR-330 'jakarta.inject.Named' annotation found and supported for component scanning");
		} catch (ClassNotFoundException ex) {
			// JSR-330 API not available - simply skip.
		}
	}

	/**
	 * Set the Environment to use when resolving placeholders and evaluating
	 * {@link Conditional @Conditional}-annotated component classes.
	 * <p>The default is a {@link StandardEnvironment}.
	 *
	 * @param environment the Environment to use
	 */
	public void setEnvironment(Environment environment) {
		Assert.notNull(environment, "Environment must not be null");
		this.environment = environment;
		this.conditionEvaluator = null;
	}

	@Override
	public final Environment getEnvironment() {
		if (this.environment == null) {
			this.environment = new StandardEnvironment();
		}
		return this.environment;
	}

	/**
	 * Return the {@link BeanDefinitionRegistry} used by this scanner, if any.
	 */
	@Nullable
	protected BeanDefinitionRegistry getRegistry() {
		return null;
	}

	/**
	 * Set the {@link ResourceLoader} to use for resource locations.
	 * This will typically be a {@link ResourcePatternResolver} implementation.
	 * <p>Default is a {@code PathMatchingResourcePatternResolver}, also capable of
	 * resource pattern resolving through the {@code ResourcePatternResolver} interface.
	 *
	 * @see org.springframework.core.io.support.ResourcePatternResolver
	 * @see org.springframework.core.io.support.PathMatchingResourcePatternResolver
	 */
	@Override
	public void setResourceLoader(@Nullable ResourceLoader resourceLoader) {
		this.resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
		this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
		this.componentsIndex = CandidateComponentsIndexLoader.loadIndex(this.resourcePatternResolver.getClassLoader());
	}

	/**
	 * Return the ResourceLoader that this component provider uses.
	 */
	public final ResourceLoader getResourceLoader() {
		return getResourcePatternResolver();
	}

	private ResourcePatternResolver getResourcePatternResolver() {
		if (this.resourcePatternResolver == null) {
			this.resourcePatternResolver = new PathMatchingResourcePatternResolver();
		}
		return this.resourcePatternResolver;
	}

	/**
	 * Set the {@link MetadataReaderFactory} to use.
	 * <p>Default is a {@link CachingMetadataReaderFactory} for the specified
	 * {@linkplain #setResourceLoader resource loader}.
	 * <p>Call this setter method <i>after</i> {@link #setResourceLoader} in order
	 * for the given MetadataReaderFactory to override the default factory.
	 */
	public void setMetadataReaderFactory(MetadataReaderFactory metadataReaderFactory) {
		this.metadataReaderFactory = metadataReaderFactory;
	}

	/**
	 * Return the MetadataReaderFactory used by this component provider.
	 */
	public final MetadataReaderFactory getMetadataReaderFactory() {
		if (this.metadataReaderFactory == null) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory();
		}
		return this.metadataReaderFactory;
	}


	/**
	 * Scan the class path for candidate components.
	 *
	 * @param basePackage the package to check for annotated classes
	 * @return a corresponding Set of autodetected bean definitions
	 */
	public Set<BeanDefinition> findCandidateComponents(String basePackage) {
		//判断是否使用Filter指定忽略包不扫描
		if (this.componentsIndex != null && indexSupportsIncludeFilters()) {
			// 指定的类
			return addCandidateComponentsFromIndex(this.componentsIndex, basePackage);
		} else {
			// 扫描指定包路径下的所有类
			return scanCandidateComponents(basePackage);
		}
	}

	/**
	 * Determine if the index can be used by this instance.
	 *
	 * @return {@code true} if the index is available and the configuration of this
	 * instance is supported by it, {@code false} otherwise
	 * @since 5.0
	 */
	private boolean indexSupportsIncludeFilters() {
		for (TypeFilter includeFilter : this.includeFilters) {
			if (!indexSupportsIncludeFilter(includeFilter)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Determine if the specified include {@link TypeFilter} is supported by the index.
	 *
	 * @param filter the filter to check
	 * @return whether the index supports this include filter
	 * @see #extractStereotype(TypeFilter)
	 * @since 5.0
	 */
	private boolean indexSupportsIncludeFilter(TypeFilter filter) {
		if (filter instanceof AnnotationTypeFilter annotationTypeFilter) {
			Class<? extends Annotation> annotationType = annotationTypeFilter.getAnnotationType();
			return (AnnotationUtils.isAnnotationDeclaredLocally(Indexed.class, annotationType) ||
					annotationType.getName().startsWith("jakarta."));
		}
		if (filter instanceof AssignableTypeFilter assignableTypeFilter) {
			Class<?> target = assignableTypeFilter.getTargetType();
			return AnnotationUtils.isAnnotationDeclaredLocally(Indexed.class, target);
		}
		return false;
	}

	/**
	 * Extract the stereotype to use for the specified compatible filter.
	 *
	 * @param filter the filter to handle
	 * @return the stereotype in the index matching this filter
	 * @see #indexSupportsIncludeFilter(TypeFilter)
	 * @since 5.0
	 */
	@Nullable
	private String extractStereotype(TypeFilter filter) {
		if (filter instanceof AnnotationTypeFilter annotationTypeFilter) {
			return annotationTypeFilter.getAnnotationType().getName();
		}
		if (filter instanceof AssignableTypeFilter assignableTypeFilter) {
			return assignableTypeFilter.getTargetType().getName();
		}
		return null;
	}

	private Set<BeanDefinition> addCandidateComponentsFromIndex(CandidateComponentsIndex index, String basePackage) {
		// 候选 BeanDefinition
		Set<BeanDefinition> candidates = new LinkedHashSet<>();
		try {
			// 类型列表
			Set<String> types = new HashSet<>();
			// 导入的类型过滤器
			for (TypeFilter filter : this.includeFilters) {
				String stereotype = extractStereotype(filter);
				if (stereotype == null) {
					throw new IllegalArgumentException("Failed to extract stereotype from " + filter);
				}
				// 从组件索引中获取通过的类型放入容器
				types.addAll(index.getCandidateTypes(basePackage, stereotype));
			}
			boolean traceEnabled = logger.isTraceEnabled();
			boolean debugEnabled = logger.isDebugEnabled();
			// 类型处理
			for (String type : types) {
				MetadataReader metadataReader = getMetadataReaderFactory().getMetadataReader(type);
				// 是否是候选组件
				if (isCandidateComponent(metadataReader)) {
					ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
					sbd.setSource(metadataReader.getResource());
					// 是否是候选组件
					if (isCandidateComponent(sbd)) {
						if (debugEnabled) {
							logger.debug("Using candidate component class from index: " + type);
						}
						candidates.add(sbd);
					} else {
						if (debugEnabled) {
							logger.debug("Ignored because not a concrete top-level class: " + type);
						}
					}
				} else {
					if (traceEnabled) {
						logger.trace("Ignored because matching an exclude filter: " + type);
					}
				}
			}
		} catch (IOException ex) {
			throw new BeanDefinitionStoreException("I/O failure during classpath scanning", ex);
		}
		return candidates;
	}

	private Set<BeanDefinition> scanCandidateComponents(String basePackage) {
		// 候选组件列表 BeanDefinition 列表
		Set<BeanDefinition> candidates = new LinkedHashSet<>();
		try {
			// 获取basePackage下所有的文件资源
			// classpath*: + replace(basePackage,'.','/') + / + **/*.class
			String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
					resolveBasePackage(basePackage) + '/' + this.resourcePattern;
			//根据路径获取资源对象
			// 转换成资源对象
			// 这里会转换成 FileSystemResource
			Resource[] resources = getResourcePatternResolver().getResources(packageSearchPath);
			boolean traceEnabled = logger.isTraceEnabled();
			boolean debugEnabled = logger.isDebugEnabled();
			// 资源处理
			for (Resource resource : resources) {
				String filename = resource.getFilename();
				if (filename != null && filename.contains(ClassUtils.CGLIB_CLASS_SEPARATOR)) {
					// Ignore CGLIB-generated classes in the classpath
					continue;
				}
				if (traceEnabled) {
					logger.trace("Scanning " + resource);
				}
				try {
					// 元数据读取器
					//Spring提供的元数据读取器可以获取类的元数据，比如类名、类中的方法、类上的注解，元数据读取器的默认实现为SimpleMetadataReader
					//
					//Spring中元数据读取器解析类时使用的是ASM技术,并不需要将class文件都加载进内存进行解析;JVM规范对class文件的加载是按需加载,不能违背该规范
					//————————————————
					//版权声明：本文为CSDN博主「sermonlizhi」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
					//原文链接：https://blog.csdn.net/sermonlizhi/article/details/120611484
					MetadataReader metadataReader = getMetadataReaderFactory().getMetadataReader(resource);
					// 下面根据类的元数据判断是否可以生成BeanDefinition
					// excludeFilters、includeFilters判断

					if (isCandidateComponent(metadataReader)) {
						//经过了过滤器和条件鉴定器之后，生成BeanDefinition，这个时候BeanDefinition的beanClass属性是beanClassName，而不是Class对象，因为此时只是扫描，还没进行加载
						//
						//还需要判断这个BeanDefinition是否可以实例化成一个对象
						// bean定义扫描

						ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
						// 设置资源对象
						sbd.setSource(resource);
						if (isCandidateComponent(sbd)) {
							if (debugEnabled) {
								logger.debug("Identified candidate component class: " + resource);
							}
							candidates.add(sbd);
						} else {
							if (debugEnabled) {
								logger.debug("Ignored because not a concrete top-level class: " + resource);
							}
						}
					} else {
						if (traceEnabled) {
							logger.trace("Ignored because not matching any filter: " + resource);
						}
					}
				} catch (FileNotFoundException ex) {
					if (traceEnabled) {
						logger.trace("Ignored non-readable " + resource + ": " + ex.getMessage());
					}
				} catch (Throwable ex) {
					throw new BeanDefinitionStoreException(
							"Failed to read candidate component class: " + resource, ex);
				}
			}
		} catch (IOException ex) {
			throw new BeanDefinitionStoreException("I/O failure during classpath scanning", ex);
		}
		return candidates;
	}


	/**
	 * Resolve the specified base package into a pattern specification for
	 * the package search path.
	 * <p>The default implementation resolves placeholders against system properties,
	 * and converts a "."-based package path to a "/"-based resource path.
	 *
	 * @param basePackage the base package as specified by the user
	 * @return the pattern specification to be used for package searching
	 */
	protected String resolveBasePackage(String basePackage) {
		return ClassUtils.convertClassNameToResourcePath(getEnvironment().resolveRequiredPlaceholders(basePackage));
	}

	/**
	 * Determine whether the given class does not match any exclude filter
	 * and does match at least one include filter.
	 *
	 * @param metadataReader the ASM ClassReader for the class
	 * @return whether the class qualifies as a candidate component
	 */
	protected boolean isCandidateComponent(MetadataReader metadataReader) throws IOException {
		//首先判断当前类是否被排除，如果与某个排除过滤器匹配则不能生成BeanDefinition，然后再判断是否能匹配到包含过滤器
		for (TypeFilter tf : this.excludeFilters) {
			if (tf.match(metadataReader, getMetadataReaderFactory())) {
				return false;
			}
		}
		// 符合includeFilters的会进行条件匹配，也就是先看有没有@Component，再看是否符合@Conditional
		//如果一个类有对应的包含过滤器它可能还不能生成BeanDefinition，还需要判断条件注解是否满足
		for (TypeFilter tf : this.includeFilters) {
			if (tf.match(metadataReader, getMetadataReaderFactory())) {
				return isConditionMatch(metadataReader);
			}
		}
		return false;
	}

	/**
	 * 条件鉴定器根据类上的注解来进行匹配
	 * <p>
	 * <p>
	 * Determine whether the given class is a candidate component based on any
	 * {@code @Conditional} annotations.
	 *
	 * @param metadataReader the ASM ClassReader for the class
	 * @return whether the class qualifies as a candidate component
	 */
	private boolean isConditionMatch(MetadataReader metadataReader) {
		if (this.conditionEvaluator == null) {
			this.conditionEvaluator =
					new ConditionEvaluator(getRegistry(), this.environment, this.resourcePatternResolver);
		}
		// 参数为该类的所有注解
		return !this.conditionEvaluator.shouldSkip(metadataReader.getAnnotationMetadata());
	}

	/**
	 * 下面代码的isIndependent()判断当前类是否是独立的，对于一个普通的内部类来说，编译后它也是一个独立的class文件，但它的实例化依赖顶级类，而顶级类和静态内部类可以直接实例化
	 * <p>
	 * isConcrete()判断该类是否是接口或抽象类，接口和抽象类编译后也是一个class文件，但它们都不能进行实例化
	 * <p>
	 * 如果该类是一个抽象类，但是有Lookup注解定义的方法，那么也可以进行实例化
	 * ————————————————
	 * 版权声明：本文为CSDN博主「sermonlizhi」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
	 * 原文链接：https://blog.csdn.net/sermonlizhi/article/details/120611484
	 * Determine whether the given bean definition qualifies as candidate.
	 * <p>The default implementation checks whether the class is not an interface
	 * and not dependent on an enclosing class.
	 * <p>Can be overridden in subclasses.
	 *
	 * @param beanDefinition the bean definition to check
	 * @return whether the bean definition qualifies as a candidate component
	 */
	protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
		AnnotationMetadata metadata = beanDefinition.getMetadata();
		return (metadata.isIndependent() && (metadata.isConcrete() ||
				(metadata.isAbstract() && metadata.hasAnnotatedMethods(Lookup.class.getName()))));
	}


	/**
	 * Clear the local metadata cache, if any, removing all cached class metadata.
	 */
	public void clearCache() {
		if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory cmrf) {
			// Clear cache in externally provided MetadataReaderFactory; this is a no-op
			// for a shared cache since it'll be cleared by the ApplicationContext.
			cmrf.clearCache();
		}
	}

}
