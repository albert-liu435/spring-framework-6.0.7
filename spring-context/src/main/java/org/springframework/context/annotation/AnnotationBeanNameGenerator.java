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

package org.springframework.context.annotation;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 注解Bean名称生成器
 * {@link BeanNameGenerator} implementation for bean classes annotated with the
 * {@link org.springframework.stereotype.Component @Component} annotation or
 * with another annotation that is itself annotated with {@code @Component} as a
 * meta-annotation. For example, Spring's stereotype annotations (such as
 * {@link org.springframework.stereotype.Repository @Repository}) are
 * themselves annotated with {@code @Component}.
 *
 * <p>Also supports Jakarta EE's {@link jakarta.annotation.ManagedBean} and
 * JSR-330's {@link jakarta.inject.Named} annotations, if available. Note that
 * Spring component annotations always override such standard annotations.
 *
 * <p>If the annotation's value doesn't indicate a bean name, an appropriate
 * name will be built based on the short name of the class (with the first
 * letter lower-cased), unless the first two letters are uppercase. For example:
 *
 * <pre class="code">com.xyz.FooServiceImpl -&gt; fooServiceImpl</pre>
 * <pre class="code">com.xyz.URLFooServiceImpl -&gt; URLFooServiceImpl</pre>
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @see org.springframework.stereotype.Component#value()
 * @see org.springframework.stereotype.Repository#value()
 * @see org.springframework.stereotype.Service#value()
 * @see org.springframework.stereotype.Controller#value()
 * @see jakarta.inject.Named#value()
 * @see FullyQualifiedAnnotationBeanNameGenerator
 * @since 2.5
 */
public class AnnotationBeanNameGenerator implements BeanNameGenerator {

	/**
	 * A convenient constant for a default {@code AnnotationBeanNameGenerator} instance,
	 * as used for component scanning purposes.
	 *
	 * @since 5.2
	 */
	public static final AnnotationBeanNameGenerator INSTANCE = new AnnotationBeanNameGenerator();

	private static final String COMPONENT_ANNOTATION_CLASSNAME = "org.springframework.stereotype.Component";

	private final Map<String, Set<String>> metaAnnotationTypesCache = new ConcurrentHashMap<>();

	/**
	 * 调用AnnotationBeanNameGenerator的generateBeanName()方法来生成beanName，如果是扫描生成的BeanDefinition，则判断注解有没有指定beanName
	 *
	 * @param definition the bean definition to generate a name for
	 * @param registry   the bean definition registry that the given definition
	 *                   is supposed to be registered with
	 * @return
	 */
	@Override
	public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
		//第一种：Bean Definition 是 AnnotatedBeanDefinition 类型
		//第二种：Bean Definition 不是 AnnotatedBeanDefinition 类型 或者 第一种处理情况得不到 Bean Name

		if (definition instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
			// 从注解中获取 beanName
			// 获取注解的value属性值
			String beanName = determineBeanNameFromAnnotation(annotatedBeanDefinition);

			if (StringUtils.hasText(beanName)) {
				// 如果存在直接返回
				// Explicit bean name found.
				return beanName;
			}
		}
		// Fallback: generate a unique default bean name.
		//如果注解没有指定，则调用buildDefaultBeanName()生成默认的beanName
		// 默认beanName
		// 类名,首字母小写
		return buildDefaultBeanName(definition, registry);
	}

	/**
	 * 第一步：提取 Bean Definition 中的注解元数据
	 * 第二步：从注解元数据中提取所有的注解类名
	 * 第三步：循环注解类名，从注解元数据中提取对应的注解属性
	 * 第四步：注解属性中尝试获取 value 对应的值
	 * 第五步：将 value 对应值判断是否是 String 类型，如果是那么就会被作为 Bean Name
	 *
	 * Derive a bean name from one of the annotations on the class.
	 *
	 * @param annotatedDef the annotation-aware bean definition
	 * @return the bean name, or {@code null} if none is found
	 */
	@Nullable
	protected String determineBeanNameFromAnnotation(AnnotatedBeanDefinition annotatedDef) {
		// 获取注解信息
		AnnotationMetadata amd = annotatedDef.getMetadata();
		// 所有注解
		Set<String> types = amd.getAnnotationTypes();
		String beanName = null;
		for (String type : types) {
			// 注解属性map对象
			AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(amd, type);
			if (attributes != null) {
				Set<String> metaTypes = this.metaAnnotationTypesCache.computeIfAbsent(type, key -> {
					Set<String> result = amd.getMetaAnnotationTypes(key);
					return (result.isEmpty() ? Collections.emptySet() : result);
				});
				if (isStereotypeWithNameValue(type, metaTypes, attributes)) {
					Object value = attributes.get("value");
					if (value instanceof String strVal && !strVal.isEmpty()) {
						if (beanName != null && !strVal.equals(beanName)) {
							throw new IllegalStateException("Stereotype annotations suggest inconsistent " +
									"component names: '" + beanName + "' versus '" + strVal + "'");
						}
						beanName = strVal;
					}
				}
			}
		}
		return beanName;
	}

	/**
	 * Check whether the given annotation is a stereotype that is allowed
	 * to suggest a component name through its annotation {@code value()}.
	 *
	 * @param annotationType      the name of the annotation class to check
	 * @param metaAnnotationTypes the names of meta-annotations on the given annotation
	 * @param attributes          the map of attributes for the given annotation
	 * @return whether the annotation qualifies as a stereotype with component name
	 */
	protected boolean isStereotypeWithNameValue(String annotationType,
												Set<String> metaAnnotationTypes, @Nullable Map<String, Object> attributes) {

		boolean isStereotype = annotationType.equals(COMPONENT_ANNOTATION_CLASSNAME) ||
				metaAnnotationTypes.contains(COMPONENT_ANNOTATION_CLASSNAME) ||
				annotationType.equals("jakarta.annotation.ManagedBean") ||
				annotationType.equals("jakarta.inject.Named");

		return (isStereotype && attributes != null && attributes.containsKey("value"));
	}

	/**
	 * 如果注解没有指定，则调用buildDefaultBeanName()生成默认的beanName
	 * =
	 * Derive a default bean name from the given bean definition.
	 * <p>The default implementation delegates to {@link #buildDefaultBeanName(BeanDefinition)}.
	 *
	 * @param definition the bean definition to build a bean name for
	 * @param registry   the registry that the given bean definition is being registered with
	 * @return the default bean name (never {@code null})
	 */
	protected String buildDefaultBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
		return buildDefaultBeanName(definition);
	}

	/**
	 * Derive a default bean name from the given bean definition.
	 * <p>The default implementation simply builds a decapitalized version
	 * of the short class name: e.g. "mypackage.MyJdbcDao" &rarr; "myJdbcDao".
	 * <p>Note that inner classes will thus have names of the form
	 * "outerClassName.InnerClassName", which because of the period in the
	 * name may be an issue if you are autowiring by name.
	 *
	 * @param definition the bean definition to build a bean name for
	 * @return the default bean name (never {@code null})
	 */
	protected String buildDefaultBeanName(BeanDefinition definition) {
		//扫描的时候如果没有指定className,将会抛出异常   // 获取 class name、
		String beanClassName = definition.getBeanClassName();
		Assert.state(beanClassName != null, "No bean class name set");
		// 获取短类名
		String shortClassName = ClassUtils.getShortName(beanClassName);
		// 首字母小写
		return StringUtils.uncapitalizeAsProperty(shortClassName);
	}

}
