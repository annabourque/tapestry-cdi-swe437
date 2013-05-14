package org.got5.tapestry5.cdi;

import static org.got5.tapestry5.cdi.BeanHelper.getInstance;
import static org.got5.tapestry5.cdi.BeanHelper.getQualifiers;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tapestry5.internal.services.ComponentClassCache;
import org.apache.tapestry5.ioc.AnnotationProvider;
import org.apache.tapestry5.ioc.ObjectLocator;
import org.apache.tapestry5.ioc.annotations.PostInjection;
import org.apache.tapestry5.ioc.annotations.Symbol;
import org.apache.tapestry5.ioc.annotations.UsesOrderedConfiguration;
import org.apache.tapestry5.ioc.services.RegistryShutdownHub;
import org.apache.tapestry5.model.MutableComponentModel;
import org.apache.tapestry5.plastic.PlasticField;
import org.apache.tapestry5.services.transform.InjectionProvider2;
import org.got5.tapestry5.cdi.internal.utils.InternalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@UsesOrderedConfiguration(InjectionProvider2.class)
public final class CDIInjectionProvider implements InjectionProvider2 {
	private final ComponentClassCache cache;
	private final ObjectLocator locator;
	@SuppressWarnings("rawtypes")
	private final Map<Class, Annotation[]> annotationsCache = new HashMap<Class, Annotation[]>();
	private final Collection<BeanInstance> instancesToRelease = new ArrayList<BeanInstance>();

	private static Logger logger = LoggerFactory.getLogger(CDIInjectionProvider.class); 

	public CDIInjectionProvider(final ObjectLocator locator, final ComponentClassCache cache) {
		this.locator = locator;
		this.cache = cache;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public boolean provideInjection(final PlasticField field, final ObjectLocator locator, final MutableComponentModel componentModel) {
		Class type = cache.forName(field.getTypeName());
		if(InternalUtils.isManagedByTapestry(
				type, 
				new AnnotationProvider(){

					@Override
					public <T extends Annotation> T getAnnotation(
							Class<T> annotationClass) {
						return field.getAnnotation(annotationClass);
					}}, 
				locator)){
			logger.debug("Field "+field.getName()+" of type "+field.getTypeName()+" is managed by Tapestry");
			return false;
		}

		logger.debug("Field "+field.getName()+" of type "+field.getTypeName()+" will be managed by CDI");

		final Class<?> fieldClass = load(field.getTypeName());
		final Annotation[] qualifiers = 
				InternalUtils.getFieldQualifiers(type, new AnnotationProvider(){

					@Override
					public <T extends Annotation> T getAnnotation(
							Class<T> annotationClass) {
						return field.getAnnotation(annotationClass);
					}});
		
		logger.debug("["+field.getName()+"]["+componentModel.getComponentClassName()+"] Qualifiers : ");
		for (Annotation annotation : qualifiers) {
			logger.debug("==> "+annotation.toString());
		}
		try {
			final BeanInstance instance = getInstance(fieldClass, qualifiers);
			final boolean resolved = instance != null && instance.isResolved();
			if (resolved) {
				field.inject(instance.getBean());
			}

			if (instance != null && instance.isReleasable()) {
				synchronized (instancesToRelease) {
					instancesToRelease.add(instance);
				}
			}
			logger.debug("Is field "+field.getName()+" of type "+field.getTypeName()+" has been succesfully managed by CDI ? "+resolved);
			return resolved;
		} catch (IllegalStateException isa) {
			logger.debug("CDI failed to manage the field "+field.getName()+" of type "+field.getTypeName());
			return false;
		}
	}

	private Class<?> load(String typeName) {
		try {
			return cache.forName(typeName);
		} catch (RuntimeException re) {
			try {
				return Thread.currentThread().getContextClassLoader().loadClass(typeName);
			} catch (ClassNotFoundException e) {
				throw re;
			}
		}
	}

	@PostInjection
	public void startupService(final RegistryShutdownHub shutdownHub) {
		shutdownHub.addRegistryShutdownListener(new ShutdownCleanUpListener(instancesToRelease));
	}

	private static class ShutdownCleanUpListener implements Runnable {
		private final Collection<BeanInstance> releasables;

		public ShutdownCleanUpListener(final Collection<BeanInstance> instancesToRelease) {
			releasables = instancesToRelease;
		}

		@Override
		public void run() {
			synchronized (releasables) { // should be useless but just to be sure
				for (BeanInstance instance : releasables) {
					instance.release();
				}
				releasables.clear();
			}
		}
	}
}
