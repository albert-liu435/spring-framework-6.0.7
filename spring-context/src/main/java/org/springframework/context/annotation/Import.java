/*
 * Copyright 2002-2019 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 该注解是用来快速给容器中导入一个Bean组件，在一些框架的底层被大量使用，例如：SpringBoot中。同样在Spring的底层源码中也大量使用，例如基于注解的声明式事务对应的注解
 * EnableTransactionManagement中就是用到了该注解给容器中又导入了一个Selector组件，然后通过Select的importSelects方法给容器中注册用来处理声明式事务的事务增强器及方法拦截器等组件，
 * 从而实现声明式事务的功能。声明式事务后续也会详细介绍其运行原理，此处知道声明式事务中用到Import注解即可。该注解是用来快速给容器中导入一个Bean组件，在一些框架的底层被大量使用，
 * 例如：SpringBoot中。同样在Spring的底层源码中也大量使用，例如基于注解的声明式事务对应的注解@EnableTransactionManagement中就是用到了该注解给容器中又导入了一个Selector组件，
 * 然后通过Select的importSelects方法给容器中注册用来处理声明式事务的事务增强器及方法拦截器等组件，从而实现声明式事务的功能。声明式事务后续也会详细介绍其运行原理，此处知道声明式事务中用到Import注解即可。
 * Indicates one or more <em>component classes</em> to import &mdash; typically
 * {@link Configuration @Configuration} classes.
 *
 * <p>Provides functionality equivalent to the {@code <import/>} element in Spring XML.
 * Allows for importing {@code @Configuration} classes, {@link ImportSelector} and
 * {@link ImportBeanDefinitionRegistrar} implementations, as well as regular component
 * classes (as of 4.2; analogous to {@link AnnotationConfigApplicationContext#register}).
 *
 * <p>{@code @Bean} definitions declared in imported {@code @Configuration} classes should be
 * accessed by using {@link org.springframework.beans.factory.annotation.Autowired @Autowired}
 * injection. Either the bean itself can be autowired, or the configuration class instance
 * declaring the bean can be autowired. The latter approach allows for explicit, IDE-friendly
 * navigation between {@code @Configuration} class methods.
 *
 * <p>May be declared at the class level or as a meta-annotation.
 *
 * <p>If XML or other non-{@code @Configuration} bean definition resources need to be
 * imported, use the {@link ImportResource @ImportResource} annotation instead.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @see Configuration
 * @see ImportSelector
 * @see ImportBeanDefinitionRegistrar
 * @see ImportResource
 * @since 3.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Import {

	/**
	 * 待注入的组件对应的class类
	 * {@link Configuration @Configuration}, {@link ImportSelector},
	 * {@link ImportBeanDefinitionRegistrar}, or regular component classes to import.
	 */
	Class<?>[] value();

}
