package org.snowfk.web.renderer.freemarker;

import java.io.File;

import javax.inject.Singleton;

import org.snowfk.web.names.WebAppFolder;

import com.google.inject.Inject;



@Singleton
public class FeemarkerTemplateNameResolver {

    @Inject
    private @WebAppFolder File webAppFolder;
    
    
    // resourcePath needs to be relative to WebAppFolder
    public String resolve(String resourcePath){
        if (resourcePath.endsWith("/")){
            resourcePath += "index";
        }
        File resourceFile = new File(webAppFolder,resourcePath + ".ftl");
        
        return getTemplateName(resourceFile);
    }
    
    private String getTemplateName(File resourceFile){
        if (resourceFile.exists()){
            String resourcePath = resourceFile.getAbsolutePath();
            //SystemOutUtil.printValue("FreemarkerPartProcessor.processPart resourcePath", resourcePath);
            File templateFile = new File(resourcePath);
            String templateName = resourcePath;
            //FIXME: THIS IS A HORRIBLE HACK (test if we have a windows naming convention C: or E: and remove it)
            if (resourcePath.length() > 2 && resourcePath.charAt(1) == ':') {
                templateName = templateFile.getPath().substring(2);
            }
            return templateName;            
        }else{
            return null;
        }        
    }    
}
