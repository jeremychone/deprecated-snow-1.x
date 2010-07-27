/* Copyright 2009 Jeremy Chone - Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.snowfk.web.renderer;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snowfk.web.WebApplication;
import org.snowfk.web.WebModule;
import org.snowfk.web.part.Part;
import org.snowfk.web.renderer.freemarker.FreemarkerUtil;
import org.snowfk.web.renderer.freemarker.HrefPartTemplateMethod;
import org.snowfk.web.renderer.freemarker.IncludeTemplateDirective;
import org.snowfk.web.renderer.freemarker.LinksDirective;
import org.snowfk.web.renderer.freemarker.MaxTemplateMethod;
import org.snowfk.web.renderer.freemarker.PathInfoMatcherTemplateMethod;
import org.snowfk.web.renderer.freemarker.SetHrefParam;
import org.snowfk.web.renderer.freemarker.TemplateDirectiveProxy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.internal.Nullable;
import com.google.inject.name.Named;

import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.cache.WebappTemplateLoader;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import freemarker.template.Template;

@Singleton
public class FreemarkerRenderer implements Renderer {
    static private Logger logger = LoggerFactory.getLogger(FreemarkerRenderer.class);
    
    private Configuration            conf = new Configuration();
    private ServletContext           servletContext;

    private File                     sfkFolder;

    private IncludeTemplateDirective includeTemplateDirective;
    private LinksDirective           linksDirective;
    private MaxTemplateMethod        maxTemplateMethod;
    private HrefPartTemplateMethod   hrefPartTemplateMethod;
    private WebApplication           webApplication;

    @Inject
    public FreemarkerRenderer(@Named("snow.snowFolder") File snowFolder, WebApplication webApplication) {
        this.sfkFolder = snowFolder;
        this.webApplication = webApplication;
    }

    @Inject
    public void injectDirectives(IncludeTemplateDirective includeTemplateDirective, LinksDirective linksDirective,
                                 MaxTemplateMethod maxTemplateMethod, HrefPartTemplateMethod hrefPartTemplateMethod) {
        this.includeTemplateDirective = includeTemplateDirective;
        this.linksDirective = linksDirective;
        this.maxTemplateMethod = maxTemplateMethod;
        this.hrefPartTemplateMethod = hrefPartTemplateMethod;
    }

    public void init() {
        File rootFile = sfkFolder.getAbsoluteFile();
        while (rootFile.getParentFile() != null) {
            rootFile = rootFile.getParentFile();
        }

        MultiTemplateLoader mtl;
        TemplateLoader[] loaders = null;

        // ////////// Set the templateLoaders ////////////////
        // if we have a servletContext, then include a webappTemplateLoader
        try {
            if (servletContext != null) {

                WebappTemplateLoader webappTemplateLoader = new WebappTemplateLoader(servletContext);

                TemplateLoader tl = new FileTemplateLoader(rootFile, true);
                loaders = new TemplateLoader[] { tl, webappTemplateLoader };

            } else {
                TemplateLoader tl = new FileTemplateLoader(rootFile, true);
                loaders = new TemplateLoader[] { tl };
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }

        if (loaders != null) {
            mtl = new MultiTemplateLoader(loaders);
            conf.setTemplateLoader(mtl);
        }

        // set the BeanWrapper
        conf.setObjectWrapper(BeansWrapper.DEFAULT_WRAPPER);
        // conf.set

        conf.setURLEscapingCharset("UTF-8");

        conf.setEncoding(Locale.US, "UTF-8");

        // now it will print "1000000" (and not "1,000,000")
        conf.setNumberFormat("0.######");

        // set the cache storage
        // conf.setCacheStorage(new MruCacheStorage(0,0));
        conf.setTagSyntax(Configuration.SQUARE_BRACKET_TAG_SYNTAX);
        conf.setWhitespaceStripping(true);

        conf.setSharedVariable("includeTemplate", includeTemplateDirective);

        conf.setSharedVariable("links", linksDirective);

        conf.setSharedVariable("max", maxTemplateMethod);

        conf.setSharedVariable("hrefPart", hrefPartTemplateMethod);

        conf.setSharedVariable("setHrefParam", new SetHrefParam());

        conf.setSharedVariable("piIs", new PathInfoMatcherTemplateMethod(PathInfoMatcherTemplateMethod.Mode.IS));

        conf.setSharedVariable("piStarts", new PathInfoMatcherTemplateMethod(PathInfoMatcherTemplateMethod.Mode.STARTS_WITH));
        
        for (WebModule module : webApplication.getWebModules()){
            for (TemplateDirectiveProxy directiveProxy : module.getTemplateDirectiveProxyList()){
                conf.setSharedVariable(directiveProxy.getName(), directiveProxy);
            }
        }
        
        

    }

    @Override
    public void processPart(Part part, Object data, Writer out) throws Exception {

        if (!(data instanceof Map)) {
            throw new Exception("FreemarkerRenderer.processPart requires 'data' to be of type 'Map'. Current data type "
                                    + ((data != null) ? data.getClass() : "null"));
        }

        Map model = (Map) data;
        String templateName = FreemarkerUtil.getTemplateNameFromPart(part);

        Template template = conf.getTemplate(templateName);

        template.process(model, out);

    }

    @Inject
    public void setServletContext(@Nullable ServletContext sc) {
        servletContext = sc;
    }

}
