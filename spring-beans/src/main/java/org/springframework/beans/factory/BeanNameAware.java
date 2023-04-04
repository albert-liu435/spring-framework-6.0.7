/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.beans.factory;

/**
 * 实现BeanNameAware接口需要实现setBeanName()方法，这个方法只是简单的返回我们当前的beanName
 * <p>
 * 这个接口表面上的作用就是让实现这个接口的bean知道自己在spring容器里的名字，而且听官方的意思是这个接口更多的使用在spring的框架代码中，
 * 实际开发环境应该不建议使用，因为spring认为bean的名字与bean的联系并不是很深，（的确，抛开spring API而言，我们如果获取了该bean的名字，其实意义不是很大，
 * 我们没有获取该bean的class，只有该bean的名字，我们也无从下手，相反，因为bean的名称在spring容器中可能是该bean的唯一标识，也就是说再beanDefinitionMap中，
 * key值就是这个name，spring可以根据这个key值获取该bean的所有特性）所以spring说这个不是非必要的依赖
 * <p>
 * Interface to be implemented by beans that want to be aware of their
 * bean name in a bean factory. Note that it is not usually recommended
 * that an object depends on its bean name, as this represents a potentially
 * brittle dependence on external configuration, as well as a possibly
 * unnecessary dependence on a Spring API.
 *
 * <p>For a list of all bean lifecycle methods, see the
 * {@link BeanFactory BeanFactory javadocs}.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see BeanClassLoaderAware
 * @see BeanFactoryAware
 * @see InitializingBean
 * @since 01.11.2003
 */
public interface BeanNameAware extends Aware {

	/**
	 * Set the name of the bean in the bean factory that created this bean.
	 * <p>Invoked after population of normal bean properties but before an
	 * init callback such as {@link InitializingBean#afterPropertiesSet()}
	 * or a custom init-method.
	 *
	 * @param name the name of the bean in the factory.
	 *             Note that this name is the actual bean name used in the factory, which may
	 *             differ from the originally specified name: in particular for inner bean
	 *             names, the actual bean name might have been made unique through appending
	 *             "#..." suffixes. Use the {@link BeanFactoryUtils#originalBeanName(String)}
	 *             method to extract the original bean name (without suffix), if desired.
	 */
	void setBeanName(String name);

}
