/* Copyright 2009 Jeremy Chone - Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.snowfk.web.method;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.snowfk.web.method.argument.WebArgRef;
import org.snowfk.web.method.argument.WebEntity;
import org.snowfk.web.method.argument.WebMap;
import org.snowfk.web.method.argument.WebParam;
import org.snowfk.web.method.argument.WebParameterParser;
import org.snowfk.web.method.argument.WebPath;
import org.snowfk.web.method.argument.WebState;
import org.snowfk.web.method.argument.WebUser;


public class BaseWebHandlerRef {
    protected Object       webHandler;
    protected Method       method;
    protected List<WebArgRef> webArgRefs = new ArrayList<WebArgRef>();
    protected Map<Class<? extends Annotation>,WebParameterParser> webParameterParserMap;
    
    public BaseWebHandlerRef(Object webHandler, Method method, Map<Class<? extends Annotation>,WebParameterParser> webParameterParserMap) {
        this.webHandler = webHandler;
        this.method = method;
        this.webParameterParserMap = webParameterParserMap;
    }
    
    protected void initWebParamRefs(){
        if (method != null){
            Class[] paramClasses = method.getParameterTypes();
            Annotation[][] paramAnnotationsArray =  method.getParameterAnnotations();
            int i = 0;
            //for each method parameter class

            for (Class paramClass : paramClasses){
                Annotation webArgumentAnnotation = getWebArgumentAnnotationFromAnnotationArray(paramAnnotationsArray[i]);
                WebArgRef webParamRef;
                if (webArgumentAnnotation instanceof WebParam){
                    webParamRef = new WebArgRef((WebParam)webArgumentAnnotation,paramClass);
                }else if (webArgumentAnnotation instanceof WebUser){
                    webParamRef = new WebArgRef((WebUser)webArgumentAnnotation,paramClass);
                }else if (webArgumentAnnotation instanceof WebPath){
                    webParamRef = new WebArgRef((WebPath)webArgumentAnnotation,paramClass);
                }else if (webArgumentAnnotation instanceof WebMap){
                    webParamRef = new WebArgRef((WebMap)webArgumentAnnotation,paramClass);
                }else if (webArgumentAnnotation instanceof WebEntity){
                    webParamRef = new WebArgRef((WebEntity)webArgumentAnnotation,paramClass);
                }else if (webArgumentAnnotation instanceof WebState){
                    webParamRef = new WebArgRef((WebState)webArgumentAnnotation,paramClass);
                }else if (webArgumentAnnotation != null && webParameterParserMap.containsKey(webArgumentAnnotation.annotationType())) {
                    webParamRef = new WebArgRef(webParameterParserMap.get(webArgumentAnnotation.annotationType()), (Annotation) webArgumentAnnotation, paramClass);
                }
                else{
                    webParamRef = new WebArgRef(paramClass);
                }
                webArgRefs.add(webParamRef);
                
                i++;
            }
        }
    }
    
    private Annotation getWebArgumentAnnotationFromAnnotationArray(Annotation[] paramAnnotations){
        
        if (paramAnnotations != null){
            for (Annotation annotation : paramAnnotations){
                if (annotation instanceof WebParam){
                    return annotation;
                }
                if (annotation instanceof WebUser){
                    return annotation;
                }
                if (annotation instanceof WebPath){
                    return annotation;
                }
                if (annotation instanceof WebMap){
                    return annotation;
                }
                if (annotation instanceof WebEntity){
                    return annotation;
                }
                if (annotation instanceof WebState){
                    return annotation;
                }
                if (webParameterParserMap.containsKey(annotation.annotationType())) {
                    return annotation;
                }
            }
        }
        return null;
    }
    
    //for the WebHandlerMethodInterceptor
    public Method getMethod(){
    	return method;
    }
    
    /*--------- Invocation Methods ---------*/
    

    /*--------- /Invocation Methods ---------*/
}
