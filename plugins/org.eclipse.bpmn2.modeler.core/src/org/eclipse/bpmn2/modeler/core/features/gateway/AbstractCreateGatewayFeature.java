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
 * @author Ivar Meikas
 ******************************************************************************/
package org.eclipse.bpmn2.modeler.core.features.gateway;

import org.eclipse.bpmn2.Gateway;
import org.eclipse.bpmn2.modeler.core.features.AbstractCreateFlowElementFeature;
import org.eclipse.graphiti.features.IFeatureProvider;

public abstract class AbstractCreateGatewayFeature<T extends Gateway> extends AbstractCreateFlowElementFeature<T> {

	public AbstractCreateGatewayFeature(IFeatureProvider fp) {
		super(fp);
	}
}
