/*******************************************************************************
 * Copyright (c) 2011, 2012 Red Hat, Inc.
 *  All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 *
 * @author Bob Brodt
 ******************************************************************************/

package org.eclipse.bpmn2.modeler.core.adapters;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.bpmn2.ExtensionAttributeValue;
import org.eclipse.bpmn2.modeler.core.ToolTipProvider;
import org.eclipse.bpmn2.modeler.core.utils.ModelUtil;
import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emf.edit.provider.ComposeableAdapterFactory;
import org.eclipse.emf.transaction.TransactionalEditingDomain;

/**
 * This class is a light weight replacement for the {@code ItemProviderAdapter}
 * classes generated by EMF for the BPMN2 model. It uses Java generics to
 * specialize the adapter for a given model object. This allows extension
 * plug-ins to provide their own implementations for BPMN2 types.
 * 
 * This can be extended by contributing plug-ins using the
 * {@code <propertyExtension>} extension point. See the
 * {@code org.eclipse.bpmn2.modeler.runtime} extension point documentation for
 * more details.
 */
public class ExtendedPropertiesAdapter<T extends EObject> extends ObjectPropertyProvider {

	/** Property key for the verbose description of this model object */
	public final static String LONG_DESCRIPTION = "long.description"; //$NON-NLS-1$
	/** Property key indicating if this object can be edited, or is read-only */
	public final static String UI_CAN_EDIT = "ui.can.edit"; //$NON-NLS-1$
	/**
	 * Any adapter that uses this must override setValue() which understands
	 * how to convert a String to the required type. This is used in
	 * ComboObjectEditor (maybe others in the future)
	 */
	public final static String UI_CAN_EDIT_INLINE = "ui.can.edit.inline"; //$NON-NLS-1$
	public final static String UI_CAN_CREATE_NEW = "ui.can.create.new"; //$NON-NLS-1$
	/**
	 * For Combo boxes (ComboObjectEditor), this indicates that an empty
	 * selection will be added to the list of possible choices; For Text fields
	 * (TextObjectEditor), this indicates that the actual value of a feature
	 * should be used as the edit field text instead of its textual
	 * representation as returned by @link ModelUtil#getDisplayName(). In this
	 * case, if the value is null, it will be replaced with an empty string.
	 */
	public final static String UI_CAN_SET_NULL = "ui.can.set.null"; //$NON-NLS-1$
	/**
	 * Property key indicating if this object is multi-valued (i.e. requires a
	 * Comob box or similar selection widget)
	 */ 
	public final static String UI_IS_MULTI_CHOICE = "ui.is.multi.choice"; //$NON-NLS-1$
	/** Property key for the the {@code ObjectDescriptor} object */
	public static final String OBJECT_DESCRIPTOR = "object.descriptor"; //$NON-NLS-1$
	/** Property key for the {@code FeatureDescriptor} object */
	public static final String FEATURE_DESCRIPTOR = "feature.descriptor"; //$NON-NLS-1$
	/** Property key for the line number in XML document where this object is defined */
	public static final String LINE_NUMBER = "line.number"; //$NON-NLS-1$
	
	/**
	 * An {@code ExtendedPropertiesAdapter} may be created with a type (EClass) and then later
	 * receive the actual object that is to be adapted {@see ExtendedPropertiesAdapter#setTarget(Notifier)}.
	 * Since we can't actually adapt the EClass itself, we need to construct dummy objects
	 * that will temporarily hold the adapter until it can receive the actual target object.  
	 */
	private static Hashtable<EClass,EObject> dummyObjects = new Hashtable<EClass,EObject>();

	/**
	 * The map of EStructuralFeatures that need {@code FeatureDescriptor}
	 * provider classes.
	 */
	private Hashtable<
		EStructuralFeature, // feature type
		Hashtable<String,Object>> // property key and value
			featureProperties = new Hashtable<EStructuralFeature, Hashtable<String,Object>>();
	
	/**
	 * The Adapter Factory that was used to construct this
	 * {@code ExtendedPropertiesAdapter}
	 */
	private AdapterFactory adapterFactory;
	
	/**
	 * Constructor that adapts the given model object.
	 * 
	 * @param adapterFactory the Adapter Factory instance.
	 * @param object the object to be adapted.
	 */
	public ExtendedPropertiesAdapter(AdapterFactory adapterFactory, T object) {
		super(object.eResource());
		this.adapterFactory = adapterFactory;
		setTarget(object);
	}
	
	/**
	 * Convenience method for creating and adapting a model object for an
	 * {@code ExtendedPropertiesAdapter}.
	 * 
	 * @param object the object to be adapted.
	 * @return the {@code ExtendedPropertiesAdapter}
	 */
	@SuppressWarnings("rawtypes")
	public static ExtendedPropertiesAdapter adapt(Object object) {
		return adapt(object,null);
	}
	
	/**
	 * Convenience method for creating and adapting a feature of a model object
	 * for an {@code ExtendedPropertiesAdapter}.
	 * 
	 * @param object the object to be adapted.
	 * @param feature a feature of the given object.
	 * @return the {@code ExtendedPropertiesAdapter}
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static ExtendedPropertiesAdapter adapt(Object object, EStructuralFeature feature) {
		ExtendedPropertiesAdapter adapter = null;
		if (object instanceof ExtensionAttributeValue)
			object = ((ExtensionAttributeValue) object).eContainer();
		if (object instanceof EObject) {
			// If the EObject already has one of these adapters, find the "best" one for
			// the given feature. The "best" means the adapter will have defined a FeatureDescriptor
			// for the given feature.
			EObject eObject = (EObject) object;
			ExtendedPropertiesAdapter genericAdapter = null;
			for (Adapter a : eObject.eAdapters()) {
				if (a instanceof ExtendedPropertiesAdapter && ((ExtendedPropertiesAdapter)a).canAdapt(eObject, feature)) {
					if (a.getClass() == ExtendedPropertiesAdapter.class)
						genericAdapter = (ExtendedPropertiesAdapter) a;
					else
						adapter = (ExtendedPropertiesAdapter) a;
				}
			}
			// if no "best" adapter is found, use the generic adapter if one has been created for this EObject
			if (adapter==null && genericAdapter!=null)
				adapter = genericAdapter;
			
			EObject eclass = getFeatureClass(eObject,feature);
			if (adapter==null)
				adapter = (ExtendedPropertiesAdapter) AdapterUtil.adapt(eclass, ExtendedPropertiesAdapter.class);
			if (adapter!=null) {
				if (eObject instanceof EClass) {
					EObject dummy = getDummyObject((EClass)eObject);
					if (dummy!=null)
						eObject = dummy;
				}
				adapter.setTarget(eObject);
				adapter.getObjectDescriptor().setObject(eObject);
				if (feature!=null)
					adapter.getFeatureDescriptor(feature).setObject(eObject);
				
				// load the description for this object from Messages
//				if (adapter.getProperty(LONG_DESCRIPTION)==null) {
//					String description = getDescription(adapter.adapterFactory, eObject);
//					if (description!=null && !description.isEmpty())
//						adapter.setProperty(LONG_DESCRIPTION, description);
//				}
			}
		}
		return adapter;
	}

	/**
	 * Dummy objects are constructed when needed for an
	 * {@code ExtendedPropertiesAdapter}. The adapter factory (@see
	 * org.eclipse.bpmn2
	 * .modeler.ui.adapters.Bpmn2EditorItemProviderAdapterFactory) knows how to
	 * construct an ExtendedPropertiesAdapter from an EClass, however the
	 * adapter itself needs an EObject. This method constructs and caches these
	 * dummy objects as they are needed.
	 * 
	 * @param eclass EClass of the object to create.
	 * @return an orphan EObject of the given EClass type.
	 */
	public static EObject getDummyObject(EClass eclass) {
		EObject object = dummyObjects.get(eclass);
		if (object==null && eclass.eContainer() instanceof EPackage && !eclass.isAbstract()) {
	    	EPackage pkg = (EPackage)eclass.eContainer();
	    	object = pkg.getEFactoryInstance().create(eclass);
			dummyObjects.put(eclass, object);
		}
		return object;
	}

	/**
	 * Returns the Adapter Factory that created this
	 * {@code ExtendedPropertiesAdapter}
	 * 
	 * @return the Adapter Factory.
	 */
	public AdapterFactory getAdapterFactory() {
		return adapterFactory;
	}
	
	/**
	 * Sets the Object Descriptor for this adapter. See {@code ObjectDescriptor}
	 * for details.
	 * 
	 * @param od the ObjectDescriptor instance.
	 */
	public void setObjectDescriptor(ObjectDescriptor<T> od) {
		setProperty(OBJECT_DESCRIPTOR,od);
		od.setOwner(this);
	}

	/**
	 * Returns the EClass of the given object's feature. If the EClass is
	 * abstract, returns the EObject itself.
	 * 
	 * @param object an EObject
	 * @param feature an EStructuralFeature of the object
	 * @return a feature EClass, or the object if the feature is abstract.
	 */
	private static EObject getFeatureClass(EObject object, EStructuralFeature feature) {
		EClass eclass = null;
		if (feature!=null && feature.eContainer() instanceof EClass) {
			eclass = (EClass)feature.eContainer();
		}
		if (eclass==null || eclass.isAbstract()) {
			return object;
		}
		return eclass;
	}

	/**
	 * Returns the Object Descriptor for this adapter. If an Object Descriptor
	 * has not been set, a default implementation is created and set for this
	 * adapter.
	 * 
	 * @return the Object Descriptor instance.
	 */
	@SuppressWarnings("unchecked")
	public ObjectDescriptor<T> getObjectDescriptor() {
		ObjectDescriptor<T> od = (ObjectDescriptor<T>) getProperty(OBJECT_DESCRIPTOR);
		if (od==null) {
			setObjectDescriptor(od = new ObjectDescriptor<T>(this, (T)getTarget()));
		}
		return od;
	}

	/**
	 * Check if a FeatureDescriptor exists for the given feature.
	 * 
	 * @param feature an EStructuralFeature
	 * @return true if the adapter has a FeatureDescriptor, false if not.
	 */
	@SuppressWarnings("unchecked")
	public boolean hasFeatureDescriptor(EStructuralFeature feature) {
		FeatureDescriptor<T> fd = (FeatureDescriptor<T>) getProperty(feature,FEATURE_DESCRIPTOR);
		return fd!=null;
	}

	/**
	 * Returns the Feature Descriptor for this adapter. If a Feature Descriptor has not been
	 * set, a default implementation is created and set for this adapter.
	 * 
	 * @param feature an EStructuralFeature
	 * @return the FeatureDescriptor instance.
	 */
	@SuppressWarnings("unchecked")
	public FeatureDescriptor<T> getFeatureDescriptor(EStructuralFeature feature) {
		FeatureDescriptor<T> fd = (FeatureDescriptor<T>) getProperty(feature,FEATURE_DESCRIPTOR);
		if (fd==null) {
			setFeatureDescriptor(feature, fd = new FeatureDescriptor<T>(this, (T)getTarget(), feature));
		}
		return fd;
	}
	
	/**
	 * Convenience method for getting the Feature Descriptor by feature name.
	 * 
	 * @param featureName name of a feature.
	 * @return same as {@code getFeatureDescriptor(EStructuralFeature)}
	 */
	public FeatureDescriptor<T> getFeatureDescriptor(String featureName) {
		EStructuralFeature feature = getFeature(featureName);
		return getFeatureDescriptor(feature);
	}
	
	/**
	 * Sets the Feature Descriptor for the given feature. Clients may use this to override
	 * default behavior for specific object features.
	 * 
	 * @param feature an EStructuralFeature
	 * @param fd the Feature Descriptor instance
	 */
	public void setFeatureDescriptor(EStructuralFeature feature, FeatureDescriptor<T> fd) {
		Hashtable<String,Object> props = featureProperties.get(feature);
		if (props==null) {
			props = new Hashtable<String,Object>();
			featureProperties.put(feature,props);
		}
		fd.setOwner(this);
		props.put(FEATURE_DESCRIPTOR, fd);
	}

	/**
	 * Lookup method for the given feature name.
	 * 
	 * @param name name of a feature
	 * @return the EStructuralFeature of the object provided by the Object Descriptor.
	 */
	public EStructuralFeature getFeature(String name) {
		EObject object = getObjectDescriptor().object;
		if (object instanceof ExtensionAttributeValue) {
			EObject container = ((ExtensionAttributeValue)object).eContainer();
			if (container!=null) {
				ExtendedPropertiesAdapter adapter = this.adapt(container);
				if (adapter!=null)
					return adapter.getFeature(name);
			}
		}
		for (Entry<EStructuralFeature, Hashtable<String, Object>> entry : featureProperties.entrySet()) {
			EStructuralFeature feature = entry.getKey();
			if (feature.getName().equals(name)) {
				return feature;
			}
		}
		return null;
	}

	/**
	 * Returns a list of all features that have Feature Descriptors in this adapter.
	 * 
	 * @return a list of EStructuralFeatures
	 */
	public List<EStructuralFeature> getFeatures() {
		EObject object = getObjectDescriptor().object;
		if (object instanceof ExtensionAttributeValue) {
			EObject container = ((ExtensionAttributeValue)object).eContainer();
			if (container!=null) {
				ExtendedPropertiesAdapter adapter = this.adapt(container);
				if (adapter!=null)
					return adapter.getFeatures();
			}
		}
		List<EStructuralFeature> features = new ArrayList<EStructuralFeature>();
		features.addAll(featureProperties.keySet());
		return features;
	}

	/**
	 * Return a list of all Object Properties.
	 * 
	 * @return a list of Object Properties
	 */
	private Hashtable <String, Object> getObjectProperties() {
		if (properties==null)
			properties = new Hashtable <String,Object>();
		return properties;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.bpmn2.modeler.core.adapters.ObjectPropertyProvider#getProperty(java.lang.String)
	 */
	public Object getProperty(String key) {
		return getObjectProperties().get(key);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.bpmn2.modeler.core.adapters.ObjectPropertyProvider#setProperty(java.lang.String, java.lang.Object)
	 * 
	 * Set the value for an Object Property.
	 * 
	 * @param key property name.
	 * @param value property value.
	 */
	public void setProperty(String key, Object value) {
		if (value==null)
			getObjectProperties().remove(key);
		else
			getObjectProperties().put(key, value);
	}

	/**
	 * Gets the value of the Feature Property.
	 *
	 * @param feature the feature
	 * @param key the key
	 * @return the property value
	 */
	public Object getProperty(EStructuralFeature feature, String key) {
		Hashtable<String,Object> props = featureProperties.get(feature);
		if (props==null) {
			props = new Hashtable<String,Object>();
			featureProperties.put(feature,props);
		}
		return props.get(key);
	}

	/**
	 * Convenience method to get the boolean value of an Object Property.
	 * 
	 * @param key the Object Property key
	 * @return true or false depending on the Object Property value. If this is
	 *         not a boolean property, return false.
	 */
	public boolean getBooleanProperty(String key) {
		Object result = getProperty(key);
		if (result instanceof Boolean)
			return ((Boolean)result);
		return false;
	}

	/**
	 * Convenience method to get the boolean value of a Feature Property.
	 * 
	 * @param feature the object's feature
	 * @param key the Feature Property key
	 * @return true or false depending on the Object Property value. If this is
	 *         not a boolean property, return false.
	 */
	public boolean getBooleanProperty(EStructuralFeature feature, String key) {
		Object result = getProperty(feature, key);
		if (result instanceof Boolean)
			return ((Boolean)result);
		return false;
	}

	/**
	 * Set the value of a Feature Property.
	 * 
	 * @param feature the object's feature
	 * @param key the Feature Property key
	 * @param value the property value
	 */
	public void setProperty(EStructuralFeature feature, String key, Object value) {
		Hashtable<String,Object> props = featureProperties.get(feature);
		if (props==null) {
			props = new Hashtable<String,Object>();
			featureProperties.put(feature,props);
		}
		props.put(key, value);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.emf.common.notify.impl.AdapterImpl#setTarget(org.eclipse.emf.common.notify.Notifier)
	 */
	public void setTarget(Notifier newTarget) {
		super.setTarget(newTarget);
		if (newTarget instanceof EObject && !(newTarget instanceof EClass)) {
			EObject object = (EObject)newTarget;
			for (Adapter a : object.eAdapters()) {
				if (a instanceof ExtendedPropertiesAdapter)
					return;
			}
			object.eAdapters().add(this);
		}
	}

	/**
	 * Check if the given object feature can be adapted.
	 *
	 * @param object the object
	 * @param feature the feature
	 * @return true, if the object has a Feature Descriptor for the given feature.
	 */
	public boolean canAdapt(EObject object, EStructuralFeature feature) {
		if (object!=null) {
			if (getObjectDescriptor().object.eClass() == object.eClass()) {
				if (feature==null)
					return true;
				// only TRUE if this adapter already has a FeatureDescriptor for this feature 
				Hashtable<String,Object> props = featureProperties.get(feature);
				if (props!=null) {
					return true;
				}
			}
		}
		return false;
	}

	public String getDescription(EObject object) {
		return ToolTipProvider.INSTANCE.getLongDescription(adapterFactory,object);
	}

	/**
	 * Compare two EObjects. The default implementation of this method compares the values of
	 * identical features of both objects. This method recognizes features that are {@code StringWrapper}
	 * (proxy) objects and compares their string values.
	 * 
	 * @param thisObject an EObject
	 * @param otherObject an EObject to be compared against thisObject.
	 * @param similar if true, then the ID attributes of the objects being compared <b>may</b> be different.
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	public static boolean compare(EObject thisObject, EObject otherObject, boolean similar) {
		for (EStructuralFeature f : thisObject.eClass().getEAllStructuralFeatures()) {
			// IDs are allowed to be different
			if (similar && "id".equals(f.getName())) //$NON-NLS-1$
				continue;
			Object v1 = otherObject.eGet(f);
			Object v2 = thisObject.eGet(f);
			// both null? equal!
			if (v1==null && v2==null)
				continue;
			// one or the other null? not equal!
			if (v1==null || v2==null)
				return false;
			// both not null? do a default compare...
			if (!v1.equals(v2)) {
				// the default Object.equals(obj) fails:
				// for Dynamic EObjects (used here as "proxies") only compare their proxy URIs 
				if (ModelUtil.isStringWrapper(v1) && ModelUtil.isStringWrapper(v2)) {
					v1 = ModelUtil.getStringWrapperValue(v1);
					v2 = ModelUtil.getStringWrapperValue(v2);
					if (v1==null && v2==null)
						continue;
					if (v1==null || v2==null)
						return false;
					if (v1.equals(v2))
						continue;
				}
				else if (v1 instanceof EObject && v2 instanceof EObject) {
					// for all other EObjects, do a deep compare...
					ExtendedPropertiesAdapter adapter = ExtendedPropertiesAdapter.adapt((EObject)v1);
					if (adapter!=null) {
						if (adapter.getObjectDescriptor().compare((EObject)v2,similar))
							continue;
					}
				}
				return false;
			}
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.bpmn2.modeler.core.adapters.ObjectPropertyProvider#getResource()
	 */
	@Override
	public Resource getResource() {
		Resource resource = super.getResource();
		if (resource==null) {
			ObjectDescriptor<T> od = (ObjectDescriptor<T>) getProperty(OBJECT_DESCRIPTOR);
			if (od!=null) {
				IResourceProvider rp = AdapterRegistry.INSTANCE.adapt(od.object.eContainer(), IResourceProvider.class);
				if (rp!=null && rp!=this)
					resource = rp.getResource();
			}
		}
		if (resource==null)
			return super.getResource();
		return resource;
	}
	

	/* (non-Javadoc)
	 * @see org.eclipse.bpmn2.modeler.core.adapters.ObjectPropertyProvider#getEditingDomain()
	 */
	public TransactionalEditingDomain getEditingDomain() {
		EditingDomain result = null;
		if (adapterFactory instanceof IEditingDomainProvider) {
			result = ((IEditingDomainProvider) adapterFactory).getEditingDomain();
		}
		if (result == null) {
			if (adapterFactory instanceof ComposeableAdapterFactory) {
				AdapterFactory rootAdapterFactory = ((ComposeableAdapterFactory) adapterFactory)
						.getRootAdapterFactory();
				if (rootAdapterFactory instanceof IEditingDomainProvider) {
					result = ((IEditingDomainProvider) rootAdapterFactory).getEditingDomain();
				}
			}
		}
		// it's gotta be a Transactional Editing Domain or nothing!
		if (result instanceof TransactionalEditingDomain)
			return (TransactionalEditingDomain)result;
		return null;
	}
}
