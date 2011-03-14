/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.ejb3.deployment.processors;

import org.jboss.as.ejb3.component.session.SessionBeanComponentDescription;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.logging.Logger;

import javax.ejb.AccessTimeout;
import javax.ejb.ConcurrencyManagementType;
import java.util.List;
import java.util.Map;

/**
 * Processes the {@link javax.ejb.AccessTimeout} annotation on a session bean, which allows concurrent access (like @Singleton and @Stateful beans),
 * and its methods and updates the {@link SessionBeanComponentDescription} accordingly.
 * <p/>
 * For optimization, this processor should run after the {@link ConcurrencyManagementAnnotationProcessor} so that {@link javax.ejb.AccessTimeout} processing
 * can be skipped for beans with {@link javax.ejb.ConcurrencyManagementType#BEAN bean managed concurrency}.
 *
 * @author Jaikiran Pai
 */
public class AccessTimeoutAnnotationProcessor extends AbstractAnnotationEJBProcessor<SessionBeanComponentDescription> {

    /**
     * Logger
     */
    private static final Logger logger = Logger.getLogger(AccessTimeoutAnnotationProcessor.class);

    private static final DotName ACCESS_TIMEOUT_ANNOTATION_DOT_NAME = DotName.createSimple(AccessTimeout.class.getName());

    @Override
    protected Class<SessionBeanComponentDescription> getComponentDescriptionType() {
        return SessionBeanComponentDescription.class;
    }

    @Override
    protected void processAnnotations(ClassInfo beanClass, CompositeIndex compositeIndex, SessionBeanComponentDescription componentDescription) throws DeploymentUnitProcessingException {
        if (!componentDescription.allowsConcurrentAccess()) {
            return;
        }
        if (componentDescription.getConcurrencyManagementType() == ConcurrencyManagementType.BEAN) {
            // skip @AccessTimeout processing for bean managed concurrency.
            logger.debug("Skipping @AccessTimeout processing for bean: " + componentDescription.getEJBName() + " with BEAN managed concurrency management");
            return;
        }
        this.processAccessTimeoutAnnotations(beanClass, compositeIndex, componentDescription);
    }

    private void processAccessTimeoutAnnotations(ClassInfo beanClass, CompositeIndex compositeIndex, SessionBeanComponentDescription componentDescription) throws DeploymentUnitProcessingException {
        final DotName superName = beanClass.superName();
        if (superName != null) {
            ClassInfo superClass = compositeIndex.getClassByName(superName);
            if (superClass != null)
                processAccessTimeoutAnnotations(superClass, compositeIndex, componentDescription);
        }

        final Map<DotName, List<AnnotationInstance>> classAnnotations = beanClass.annotations();
        if (classAnnotations == null) {
            return;
        }

        List<AnnotationInstance> annotations = classAnnotations.get(ACCESS_TIMEOUT_ANNOTATION_DOT_NAME);
        if (annotations == null) {
            return;
        }

        for (AnnotationInstance annotationInstance : annotations) {
            AnnotationTarget target = annotationInstance.target();
            AccessTimeout accessTimeout = (AccessTimeout) annotationInstance.value().value();
            if (target instanceof ClassInfo) {
                // bean level
                componentDescription.setBeanLevelAccessTimeout(accessTimeout);
                logger.debug("Bean " + componentDescription.getEJBName() + " marked for access timeout: " + accessTimeout);
            } else if (target instanceof MethodInfo) {
                // method specific access timeout
                final MethodInfo method = (MethodInfo) target;
                String methodName = method.name();
                String[] methodParams = toString(method.args());
                componentDescription.setAccessTimeout(accessTimeout, methodName, methodParams);
                logger.debug("Method " + method.name() + methodParams + " on bean " + componentDescription.getEJBName() + " marked for access timeout: " + accessTimeout);
            }
        }
    }

    private String[] toString(Object[] a) {
        if (a == null) {
            return null;
        }
        final String[] result = new String[a.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = a[i].toString();
        }
        return result;
    }
}
