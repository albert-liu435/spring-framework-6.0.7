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

package org.springframework.beans.factory.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 循环依赖问题 https://mp.weixin.qq.com/s?__biz=MzU0OTE4MzYzMw==&mid=2247545567&idx=2&sn=8478f342befd6d2d84e3e11c635c4952&chksm=fbb1bb21ccc63237a4890e75a3b43a50b69ef88900fac7e784916fdff134cc94a058c192b63f&scene=27
 * <p>
 * 共享bean实例的通用注册表，实现了SingletonBeanRegistry. 允许注册表中注册的单例应该被所有调用者共享，通过bean名称获得。
 * 还支持登记的DisposableBean实例，（这可能会或不能正确的注册单例），关闭注册表时destroyed.
 * 可以注册bean之间的依赖关系，执行适当的关闭顺序。
 * <p>
 * <p>
 * 这个类主要用作基类的BeanFactory实现， 提供基本的管理
 * singleton bean 实例功能。
 *
 *
 * <p>
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 * @since 2.0
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	//singletonObjects ：ConcurrentHashMap。最简单最重要的缓存Map。保存关系是 beanName ：bean实例关系。单例的bean在创建完成后都会保存在 singletonObjects 中，后续使用直接从singletonObjects 中获取。
	//singletonFactories ：HashMap。这是为了解决循环依赖问题，用于提前暴露对象，保存形式是 beanName : ObjectFactory<?>。
	//earlySingletonObjects ：HashMap。这也是为了解决循环依赖问题。和 singletonFactories 互斥。因为 singletonFactories 保存的是 ObjectFactory。而earlySingletonObjects 个人认为是 singletonFactories 更进一步的缓存，保存的是 ObjectFactory#getObject的结果。
	//registeredSingletons ：LinkedHashSet，用于保存注册过的beanName，
	//singletonsCurrentlyInCreation ： 保存当前正在创建的bean。当一个bean开始创建时将保存其beanName，创建完成后将其移除
	//dependentBeanMap ：保存bean的依赖关系，比如A对象依赖于 B对象，会出现 B ：A。即保存的是key 被value依赖
	//dependenciesForBeanMap ：保存bean的依赖关系，不过和dependentBeanMap 反了过来。A对象依赖于 B对象，会出现 A ：B。保存的是key 依赖于 value
	//————————————————
	//版权声明：本文为CSDN博主「猫吻鱼」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
	//原文链接：https://blog.csdn.net/qq_36882793/article/details/105667530


	/**
	 * Maximum number of suppressed exceptions to preserve.
	 */
	private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;


	/**
	 * 第一级缓存：singletonObjects，用于保存实例化、注入、初始化完成的 bean 实例；
	 * Cache of singleton objects: bean name to bean instance.
	 */
	//	用于保存BeanName和创建bean实例之间的关系，即缓存bean。 beanname -> instance
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/**
	 * 第三级缓存：singletonFactories，用于保存 bean 创建工厂，以便后面有机会创建代理对象。
	 * Cache of singleton factories: bean name to ObjectFactory.
	 */
	// 用于保存BeanName和常见bean的工厂之间的关系。beanname-> ObjectFactory
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	/**
	 * 第二级缓存：earlySingletonObjects，用于保存实例化完成的 bean 实例；
	 * Cache of early singleton objects: bean name to bean instance.
	 */
	// 也是保存BeanName和创建bean实例之间的关系，与singletonObjects 不同的是，如果一个单例bean被保存在此，则当bean还在创建过程中(比如 A类中有B类属性，当创建A类时发现需要先创建B类，这时候Spring又跑去创建B类，A类就会添加到该集合中，表示正在创建)，就可以通过getBean方法获取到了，其目的是用来检测循环引用。
	private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

	/**
	 * Set of registered singletons, containing the bean names in registration order.
	 */
	// 用来保存当前所有已经注册的bean
	private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

	/**
	 * Names of beans that are currently in creation.
	 */
	// 用来保存当前正在创建的Bean。也是为了解决循环依赖的问题

	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/**
	 * Names of beans currently excluded from in creation checks.
	 */
	// 用来保存当前从创建检查中排除的bean名称

	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/**
	 * Collection of suppressed Exceptions, available for associating related causes.
	 */
	// 初始化过程中的异常列表
	@Nullable
	private Set<Exception> suppressedExceptions;

	/**
	 * 用来标记是否正在进行清除（摧毁）状态
	 * Flag that indicates whether we're currently within destroySingletons.
	 */
	private boolean singletonsCurrentlyInDestruction = false;

	/**
	 * Disposable bean instances: bean name to disposable instance.
	 */
	private final Map<String, Object> disposableBeans = new LinkedHashMap<>();

	/**
	 * Bean 包含关系容器
	 * key：Bean Name
	 * value：包含 key 的 Bean Name 列表
	 * Map between containing bean names: bean name to Set of bean names that the bean contains.
	 */
	// 包含的Bean名称之间的映射：BeanName  -> Bean包含的BeanName集合
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/**
	 * Bean 依赖关系容器
	 * key：Bean Name
	 * value：依赖 key 的 Bean Name 列表
	 * Map between dependent bean names: bean name to Set of dependent bean names.
	 */
	// bean dependent(依赖的集合) : beanName -> 依赖该beanName 的 bean，即 key代表的bean 被value 所依赖
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/**
	 * Bean 依赖关系容器
	 * Map between depending bean names: bean name to Set of bean names for the bean's dependencies.
	 */
	// bean 被哪些bean依赖 ：  beanName -> beanName 所依赖的 bean。即 key 依赖于value这些bean
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);


	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		synchronized (this.singletonObjects) {
			//如果 singletonObjects 缓存找到有指定名称为 beanName 的对象，则表示该了bean 已经注册
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				throw new IllegalStateException("Could not register object [" + singletonObject +
						"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
			}
			//若该名称没被占用，真正的注册操作在这里实现
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * Add the given singleton object to the singleton cache of this factory.
	 * <p>To be called for eager registration of singletons.
	 *
	 * @param beanName        the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {

			this.singletonObjects.put(beanName, singletonObject);
			// singletonFactories、earlySingletonObjects 解决创建 singletonObject 的过程中的循环依赖用的
			// bean 创建完成后就没用的，需要销毁
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			// beanName放进单例注册表中
			this.registeredSingletons.add(beanName);
		}
	}

	/**
	 * 添加名称为 beanName 的 singletonFactory 对象
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for eager registration of singletons, e.g. to be able to
	 * resolve circular references.
	 *
	 * @param beanName         the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			// 同理，bean 如果已经创建完成，就没有必要进行这步操作了
			if (!this.singletonObjects.containsKey(beanName)) {
				this.singletonFactories.put(beanName, singletonFactory);
				this.earlySingletonObjects.remove(beanName);
				this.registeredSingletons.add(beanName);
			}
		}
	}

	@Override
	@Nullable
	public Object getSingleton(String beanName) {
		return getSingleton(beanName, true);
	}

	/**
	 * 这个方法因为涉及循环依赖的检测，以及涉及很多变量的记录存取，所以让很多读者摸不着头脑。这个方法首先尝试从 singletonObjects 里面获取实例，如果获取不到再从 earlySingletonObjects 里里面获取，如果还获取不到，再尝试从 singletonFactories 里面获取 beanName 对应的 ObjectFactory，然后调用这个 ObjectFactory 的 getobject来创建bean，并放到 earlySingletonObjects 里面去，
	 * 并且从 earlySingletonObjects 里面 remove 掉这个 ObjectFactory，而对于后续的所有内存操作都只为了循环依赖检测时候使用，也就是在 allowEarlyReference 为 true 的情况下才会使用。
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Checks already instantiated singletons and also allows for an early
	 * reference to a currently created singleton (resolving a circular reference).
	 *
	 * @param beanName            the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not
	 * @return the registered singleton object, or {@code null} if none found
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		// Quick check for existing instance without full singleton lock
		//singletonObjects，用于保存实例化、注入、初始化完成的 bean 实例；
		Object singletonObject = this.singletonObjects.get(beanName);
		// singletonObject = nul 说明 bean 还未创建或正在创建
		// 如果 isSingletonCurrentlyInCreation(beanName) = true，说明出现了循环引用
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			//earlySingletonObjects，用于保存实例化完成的 bean 实例；
			singletonObject = this.earlySingletonObjects.get(beanName);
			if (singletonObject == null && allowEarlyReference) {

				synchronized (this.singletonObjects) {
					// Consistent creation of early reference within full singleton lock
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						singletonObject = this.earlySingletonObjects.get(beanName);
						if (singletonObject == null) {
							//singletonFactories，用于保存 bean 创建工厂，以便后面有机会创建代理对象
							// 当某些方法需要提前初始化的时候则会调用 addSingletonFactory 方法
							// 将对应的 ObjectFactory 初始化策略存储在 singletonFactories
							ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
							if (singletonFactory != null) {
								// 调用预先设定的 getObject 方法
								// 初始化 bean
								singletonObject = singletonFactory.getObject();
								// 记录在缓存中，earlySingletonObjects 与 singletonFactories 互斥
								// 显然 earlySingletonObjects、singletonFactories 是不能共存的
								this.earlySingletonObjects.put(beanName, singletonObject);
								this.singletonFactories.remove(beanName);
							}
						}
					}
				}
			}
		}
		return singletonObject;
	}

	/**
	 * 真正的根据 ObjectFactory 创建 singletonObject 对象了
	 * Return the (raw) singleton object registered under the given name,
	 * creating and registering a new one if none registered yet.
	 *
	 * @param beanName         the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 *                         with, if necessary
	 * @return the registered singleton object
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "Bean name must not be null");
		// 全局变量需要同步
		synchronized (this.singletonObjects) {
			// 首先检查对应的 bean 是否已经加载过
			// 因为 singleton 模式其实就是复用已创建的 bean，所以这一步是必须的
			Object singletonObject = this.singletonObjects.get(beanName);
			//1. 如果 singletonObject 还没有被创建，那就开始创建吧
			if (singletonObject == null) {
				//2. 如果 beanName 正在被销毁，那还创建个啥，直接抛出异常就好
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
									"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				//3. 单例对象创建前的回调，一个重要的工作就是将该 bean 打上正在创建的标记
				beforeSingletonCreation(beanName);
				// beanName 创建成功与否的一个标记
				boolean newSingleton = false;
				// 判断存储异常相关原因的集合是否已存在，不存在就创建一个
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<>();
				}
				try {
					//4. 好了，创建 singletonObject
					singletonObject = singletonFactory.getObject();
					newSingleton = true;
				} catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the meantime ->
					// if yes, proceed with it since the exception indicates that state.
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				} catch (BeanCreationException ex) {
					// 记录异常，没有什么好说的
					if (recordSuppressedExceptions) {
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				} finally {
					if (recordSuppressedExceptions) {
						this.suppressedExceptions = null;
					}
					//5. 单例对象创建后的回调，一个重要的工作就是将该 bean 正在创建的标记销毁
					afterSingletonCreation(beanName);
				}
				//6. beanName 创建成功，注册创建后的单例
				if (newSingleton) {
					addSingleton(beanName, singletonObject);
				}
			}
			return singletonObject;
		}
	}

	/**
	 * 注册 singeton bean 实例创建过程中的异常
	 * Register an exception that happened to get suppressed during the creation of a
	 * singleton bean instance, e.g. a temporary circular reference resolution problem.
	 * <p>The default implementation preserves any given exception in this registry's
	 * collection of suppressed exceptions, up to a limit of 100 exceptions, adding
	 * them as related causes to an eventual top-level {@link BeanCreationException}.
	 *
	 * @param ex the Exception to register
	 * @see BeanCreationException#getRelatedCauses()
	 */
	protected void onSuppressedException(Exception ex) {
		synchronized (this.singletonObjects) {
			if (this.suppressedExceptions != null && this.suppressedExceptions.size() < SUPPRESSED_EXCEPTIONS_LIMIT) {
				this.suppressedExceptions.add(ex);
			}
		}
	}

	/**
	 * 销毁 beanName
	 * Remove the bean with the given name from the singleton cache of this factory,
	 * to be able to clean up eager registration of a singleton if creation failed.
	 *
	 * @param beanName the name of the bean
	 * @see #getSingletonMutex()
	 */
	protected void removeSingleton(String beanName) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.remove(beanName);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.remove(beanName);
		}
	}

	/**
	 * singletonBeanRegistry 接口的 containsSingleton 方法实现
	 *
	 * @param beanName the name of the bean to look for
	 * @return
	 */
	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	@Override
	public String[] getSingletonNames() {
		// 对 singletonObjects 加锁，可能是为了防止 registeredSingletons 和 singletonObjects 出现不一致的问题
		synchronized (this.singletonObjects) {
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}


	/**
	 * 将正在创建的 beanName 从 inCreationCheckExclusions 集合删除，已创建完成的加入 inCreationCheckExclusions
	 *
	 * @param beanName
	 * @param inCreation
	 */
	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		} else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	// 返回 beanName 是否正在创建，即出现了循环引用

	/**
	 * Return whether the specified singleton bean is currently in creation
	 * (within the entire factory).
	 *
	 * @param beanName the name of the bean
	 */
	public boolean isSingletonCurrentlyInCreation(@Nullable String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	// 单例对象创建前的回调
	// 创建前 singletonsCurrentlyInCreation 注册该 beanName，如果已经添加则会抛出异常

	/**
	 * Callback before singleton creation.
	 * <p>The default implementation register the singleton as currently in creation.
	 *
	 * @param beanName the name of the singleton about to be created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	// 单例对象创建后的回调
	// 创建后 singletonsCurrentlyInCreation 移除该 beanName，如果该 beanName 已经创建完成则会抛出异常

	/**
	 * Callback after singleton creation.
	 * <p>The default implementation marks the singleton as not in creation anymore.
	 *
	 * @param beanName the name of the singleton that has been created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * Add the given bean to the list of disposable beans in this registry.
	 * <p>Disposable beans usually correspond to registered singletons,
	 * matching the bean name but potentially being a different instance
	 * (for example, a DisposableBean adapter for a singleton that does not
	 * naturally implement Spring's DisposableBean interface).
	 *
	 * @param beanName the name of the bean
	 * @param bean     the bean instance
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * 注册两个 bean 之间的控制关系
	 * Register a containment relationship between two beans,
	 * e.g. between an inner bean and its containing outer bean.
	 * <p>Also registers the containing bean as dependent on the contained bean
	 * in terms of destruction order.
	 *
	 * @param containedBeanName  the name of the contained (inner) bean
	 * @param containingBeanName the name of the containing (outer) bean
	 * @see #registerDependentBean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		// 先做一个简单的判断，避免使用 synchronized 关键字
		synchronized (this.containedBeanMap) {
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		// 紧接着调用注册内部 bean 和外部 bean 的依赖关系的方法
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * 注册给定bean的一个依赖bean，给定的bean销毁之前被销毁。
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 *
	 * @param beanName          the name of the bean
	 * @param dependentBeanName the name of the dependent bean
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		// 调用 SimpleAliasRegistry 的 canonicalName 方法，这方法是将参数 beanName 当做别名寻找到注册名，并依此递归
		String canonicalName = canonicalName(beanName);

		// 注册依赖 beanName 的集合，毫无疑问 containingBeanName 依赖 containedBeanName
		// 换句话说，依赖 containedBeanName 的集合中包括 containingBeanName
		synchronized (this.dependentBeanMap) {
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}
		// 注册 dependentBeanName 所依赖的集合
		synchronized (this.dependenciesForBeanMap) {
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * Determine whether the specified dependent bean has been registered as
	 * dependent on the given bean or on any of its transitive dependencies.
	 *
	 * @param beanName          the name of the bean to check
	 * @param dependentBeanName the name of the dependent bean
	 * @since 4.0
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		String canonicalName = canonicalName(beanName);
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans == null) {
			return false;
		}
		if (dependentBeans.contains(dependentBeanName)) {
			return true;
		}
		for (String transitiveDependency : dependentBeans) {
			if (alreadySeen == null) {
				alreadySeen = new HashSet<>();
			}
			alreadySeen.add(beanName);
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}
	//  确定是否还存在名为 beanName 的被依赖关系
	/**
	 * Determine whether a dependent bean has been registered for the given name.
	 *
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 *
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 *
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 */
	public String[] getDependenciesForBean(String beanName) {
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		if (dependenciesForBean == null) {
			return new String[0];
		}
		synchronized (this.dependenciesForBeanMap) {
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	public void destroySingletons() {
		if (logger.isTraceEnabled()) {
			logger.trace("Destroying singletons in " + this);
		}
		synchronized (this.singletonObjects) {
			this.singletonsCurrentlyInDestruction = true;
		}

		//记录需要进行摧毁的 Bean Name
		String[] disposableBeanNames;
		synchronized (this.disposableBeans) {
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			destroySingleton(disposableBeanNames[i]);
		}

		this.containedBeanMap.clear();
		this.dependentBeanMap.clear();
		this.dependenciesForBeanMap.clear();

		clearSingletonCache();
	}

	/**
	 * Clear all cached singleton instances in this registry.
	 *
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		synchronized (this.singletonObjects) {
			this.singletonObjects.clear();
			this.singletonFactories.clear();
			this.earlySingletonObjects.clear();
			this.registeredSingletons.clear();
			this.singletonsCurrentlyInDestruction = false;
		}
	}

	/**
	 * Destroy the given bean. Delegates to {@code destroyBean}
	 * if a corresponding disposable bean instance is found.
	 *
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// Remove a registered singleton of the given name, if any.
		removeSingleton(beanName);

		// Destroy the corresponding DisposableBean instance.
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
		}
		destroyBean(beanName, disposableBean);
	}

	/**
	 * Destroy the given bean. Must destroy beans that depend on the given
	 * bean before the bean itself. Should not throw any exceptions.
	 *
	 * @param beanName the name of the bean
	 * @param bean     the bean instance to destroy
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// Trigger destruction of dependent beans first...
		Set<String> dependencies;
		synchronized (this.dependentBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			// 移除依赖bean
			dependencies = this.dependentBeanMap.remove(beanName);
		}
		if (dependencies != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
			}
			for (String dependentBeanName : dependencies) {
				// 依赖列表里面的也删除
				destroySingleton(dependentBeanName);
			}
		}

		// Actually destroy the bean now...
		if (bean != null) {
			try {
				bean.destroy();
			} catch (Throwable ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
				}
			}
		}

		// Trigger destruction of contained beans...
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			// 别名列表
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			// 删除 别名列表中的beanName
			for (String containedBeanName : containedBeans) {
				destroySingleton(containedBeanName);
			}
		}

		// Remove destroyed bean from other beans' dependencies.
		synchronized (this.dependentBeanMap) {
			// 依赖beanMap中删除beanName
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				dependenciesToClean.remove(beanName);
				if (dependenciesToClean.isEmpty()) {
					it.remove();
				}
			}
		}

		// Remove destroyed bean's prepared dependency information.
		this.dependenciesForBeanMap.remove(beanName);
	}

	/**
	 * Exposes the singleton mutex to subclasses and external collaborators.
	 * <p>Subclasses should synchronize on the given Object if they perform
	 * any sort of extended singleton creation phase. In particular, subclasses
	 * should <i>not</i> have their own mutexes involved in singleton creation,
	 * to avoid the potential for deadlocks in lazy-init situations.
	 */
	@Override
	public final Object getSingletonMutex() {
		return this.singletonObjects;
	}

}
