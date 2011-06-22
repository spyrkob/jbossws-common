/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.ws.common.deployment;

import java.util.ResourceBundle;
import java.util.StringTokenizer;

import javax.jws.WebService;

import org.jboss.ws.api.annotation.WebContext;
import org.jboss.ws.api.util.BundleUtils;
import org.jboss.ws.common.Constants;
import org.jboss.ws.common.integration.AbstractDeploymentAspect;
import org.jboss.wsf.spi.deployment.Deployment;
import org.jboss.wsf.spi.deployment.Endpoint;
import org.jboss.wsf.spi.deployment.HttpEndpoint;
import org.jboss.wsf.spi.metadata.j2ee.EJBArchiveMetaData;
import org.jboss.wsf.spi.metadata.j2ee.EJBMetaData;
import org.jboss.wsf.spi.metadata.j2ee.JSEArchiveMetaData;

/**
 * A deployer that assigns the URLPattern to endpoints. 
 *
 * @author Thomas.Diesler@jboss.org
 * @since 19-May-2007
 */
public class URLPatternDeploymentAspect extends AbstractDeploymentAspect
{
   private static final ResourceBundle bundle = BundleUtils.getBundle(URLPatternDeploymentAspect.class);

   @Override
   public void start(Deployment dep)
   {
      for (Endpoint ep : dep.getService().getEndpoints())
      {
         if (ep instanceof HttpEndpoint)
         {
            HttpEndpoint httpEp = (HttpEndpoint)ep;
            String urlPattern = httpEp.getURLPattern();
            if (urlPattern == null)
            {
               urlPattern = getExplicitPattern(dep, ep);
               if (urlPattern == null)
                  urlPattern = getImplicitPattern(dep, ep);
   
               // Always prefix with '/'
               if (urlPattern.startsWith("/") == false)
                  urlPattern = "/" + urlPattern;
   
               httpEp.setURLPattern(urlPattern);
            }
         }
      }
   }

   protected String getExplicitPattern(Deployment dep, Endpoint ep)
   {
      String urlPattern = null;

      // #1 For JSE lookup the url-pattern from the servlet mappings 
      JSEArchiveMetaData webMetaData = dep.getAttachment(JSEArchiveMetaData.class);
      if (webMetaData != null)
      {
         String epName = ep.getShortName();
         urlPattern = webMetaData.getServletMappings().get(epName);
         if (urlPattern == null)
            throw new IllegalStateException(BundleUtils.getMessage(bundle, "CANNOT_OBTAIN_SERVLET_MAPPING_FOR",  epName));
      }

      // #2 Use the explicit urlPattern from port-component/port-component-uri
      EJBArchiveMetaData appMetaData = dep.getAttachment(EJBArchiveMetaData.class);
      if (appMetaData != null && appMetaData.getBeanByEjbName(ep.getShortName()) != null)
      {
         EJBMetaData bmd = appMetaData.getBeanByEjbName(ep.getShortName());
         urlPattern = bmd.getPortComponentURI();
         if (urlPattern != null)
         {
            String contextRoot = dep.getService().getContextRoot();

            if (urlPattern.startsWith("/") == false)
               urlPattern = "/" + urlPattern;

            StringTokenizer st = new StringTokenizer(urlPattern, "/");
            if (st.countTokens() > 1 && urlPattern.startsWith(contextRoot + "/"))
            {
               urlPattern = urlPattern.substring(contextRoot.length());
            }
         }
      }

      // #3 For EJB use @WebContext.urlPattern
      if (urlPattern == null)
      {
         Class beanClass = ep.getTargetBeanClass();
         WebContext anWebContext = (WebContext)beanClass.getAnnotation(WebContext.class);
         if (anWebContext != null && anWebContext.urlPattern().length() > 0)
         {
            urlPattern = anWebContext.urlPattern();
         }
         else if (!Constants.BC_CONTEXT_MODE)
         {
            WebService webServiceAnnotation = (WebService)beanClass.getAnnotation(WebService.class);
            String name = webServiceAnnotation != null ? webServiceAnnotation.name() : null;
            if (name != null && name.length() > 0)
            {
               urlPattern = name;
            }
            else
            {
               String fullClassName = beanClass.getName();
               urlPattern = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
            }
            if (webServiceAnnotation != null && !"".equals(webServiceAnnotation.serviceName()))
            {
               urlPattern = webServiceAnnotation.serviceName() + "/" + urlPattern;
            }
            
         }

      }

      return urlPattern;
   }

   protected String getImplicitPattern(Deployment dep, Endpoint ep)
   {
      // #4 Fallback to the ejb-name 
      String urlPattern = ep.getShortName();
      return urlPattern;
   }

}
