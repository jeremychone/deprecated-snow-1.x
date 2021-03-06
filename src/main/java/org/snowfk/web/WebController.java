package org.snowfk.web;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snowfk.util.FileUtil;
import org.snowfk.util.MapUtil;
import org.snowfk.web.auth.Auth;
import org.snowfk.web.auth.AuthService;
import org.snowfk.web.db.hibernate.HibernateSessionInViewHandler;
import org.snowfk.web.part.ContextModelBuilder;
import org.snowfk.web.renderer.WebBundleManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
public class WebController {
    static private Logger                 logger                        = LoggerFactory.getLogger(WebController.class);
    
    public enum ResponseType{
        template,json,other;
    }

    private static final String           CHAR_ENCODING                 = "UTF-8";
    private static final String           MODEL_KEY_REQUEST             = "r";
    public static int                     BUFFER_SIZE                   = 2048 * 2;

    private ServletFileUpload             fileUploader;

    @Inject
    private HttpWriter                    httpWriter;

    @Inject(optional=true)
    private ServletContext                servletContext;
    @Inject
    private Application                   application;
    @Inject
    private WebBundleManager              webBundleManager;

    @Inject(optional = true)
    private AuthService                   authService;

    @Inject(optional = true)
    private HibernateSessionInViewHandler hibernateSessionInViewHandler = null;

    @Inject(optional = true)
    private RequestLifeCycle              requestLifeCycle              = null;

    @Inject
    private FramePathResolver             framePathResolver;

    @Inject
    private ResourcePathResolver          resourcePathResolver;

    @Inject
    private PathFileResolver              pathFileResolver;

    @Inject
    private ActionNameResolver            actionNameResolver;

    private ThreadLocal<RequestContext>   requestContextTl              = new ThreadLocal<RequestContext>();

    private CurrentRequestContextHolder   currentRequestContextHolder   = new CurrentRequestContextHolder() {
                                                                            @Override
                                                                            public RequestContext getCurrentRequestContext() {

                                                                                return requestContextTl.get();
                                                                            }
                                                                        };

    // will be injected from .properties file
    private boolean                       ignoreTemplateNotFound        = false;

    public CurrentRequestContextHolder getCurrentRequestContextHolder() {
        return currentRequestContextHolder;
    }

    // --------- Injects --------- //
    @Inject(optional = true)
    public void injectIgnoreTemplateNotFound(@Named("snow.ignoreTemplateNotFound") String ignore) {
        if ("true".equalsIgnoreCase(ignore)) {
            ignoreTemplateNotFound = true;
        }
    }

    public void init() {
        application.init();

        /* --------- Initialize the FileUploader --------- */
        // Create a factory for disk-based file items
        DiskFileItemFactory factory = new DiskFileItemFactory();
        // Set factory constraints
        // factory.setSizeThreshold(yourMaxMemorySize);
        // factory.setRepository(yourTempDirectory);

        fileUploader = new ServletFileUpload(factory);
        /* --------- /Initialize the FileUploader --------- */
    }

    public void destroy() {
        application.shutdown();
    }

    public void service(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request.setCharacterEncoding(CHAR_ENCODING);
        response.setCharacterEncoding(CHAR_ENCODING);

        RequestContext rc = new RequestContext(request, response, servletContext, fileUploader);
        service(rc);
    }
    
    public void service(RequestContext rc) throws Exception{
        
        requestContextTl.set(rc);
        
        HttpServletRequest request = rc.getReq();
        ResponseType responseType = null;
        
        String resourcePath = resourcePathResolver.resolve(rc);
                
        
        if (isTemplatePath(resourcePath)) {
            String framePath = framePathResolver.resolve(rc);
            rc.setFramePath(framePath);
            rc.setResourcePath(fixTemplateAndJsonResourcePath(resourcePath));
            responseType = ResponseType.template;
        } else if (isJsonPath(resourcePath)) {
            rc.setResourcePath(fixTemplateAndJsonResourcePath(resourcePath));
            responseType = ResponseType.json;
        } else {
            responseType = ResponseType.other;
            rc.setResourcePath(resourcePath);
        }        
        
        try {
            // --------- Open HibernateSession --------- //
            if (hibernateSessionInViewHandler != null) {
                hibernateSessionInViewHandler.openSessionInView();
            }
            // --------- /Open HibernateSession --------- //

            // --------- Auth --------- //
            if (authService != null) {
                Auth<?> auth = authService.authRequest(rc);
                rc.setAuth(auth);
            }
            // --------- /Auth --------- //

            // --------- RequestLifeCycle Start --------- //
            if (requestLifeCycle != null) {
                requestLifeCycle.start(rc);
            }
            // --------- /RequestLifeCycle Start --------- //

            // --------- Processing the Post (if any) --------- //
            if ("POST".equals(request.getMethod())) {
                String actionName = actionNameResolver.resolve(rc);
                if (actionName != null) {
                    WebActionResponse webActionResponse = null;
                    try {
                        webActionResponse = application.processWebAction(actionName, rc);

                    } catch (Throwable e) {
                        if (e instanceof InvocationTargetException) {
                            e = e.getCause();
                        }
                        // TODO Need to handle exception
                        logger.error(getLogErrorString(e));
                        webActionResponse = new WebActionResponse(e);
                    }
                    rc.setWebActionResponse(webActionResponse);
                }

                // --------- afterActionProcessing --------- //
                if (hibernateSessionInViewHandler != null) {
                    hibernateSessionInViewHandler.afterActionProcessing();
                }
                // --------- /afterActionProcessing --------- //
            }
            // --------- /Processing the Post (if any) --------- //
            
            switch(responseType){
                case template: 
                    serviceTemplate(rc);
                    break;
                case json:
                    serviceJson(rc);
                    break;
                case other:
                    serviceFallback(rc);
                    break;
            }

            // this catch is for when this exception is thrown prior to entering the web handler method.
            // (e.g. a WebHandlerMethodInterceptor).
        } catch (AbortWithHttpStatusException e) {
            sendHttpError(rc, e.getStatus(), e.getMessage());
        } catch (AbortWithHttpRedirectException e) {
            sendHttpRedirect(rc, e);
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException) {
                e = e.getCause();
            }

            // and now we have to double-handle this one b/c it will be propagated as an InvocationTargetException
            // when it's thrown from within a web handler.
            if (e instanceof AbortWithHttpStatusException) {
                sendHttpError(rc, ((AbortWithHttpStatusException) e).getStatus(), e.getMessage());
            } else if (e instanceof AbortWithHttpRedirectException) {
                sendHttpRedirect(rc, (AbortWithHttpRedirectException) e);
            } else {
                // and this is the normal case...
                sendHttpError(rc, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.toString());
                logger.error(getLogErrorString(e));
            }

        } finally {
            // --------- RequestLifeCycle End --------- //
            if (requestLifeCycle != null) {
                requestLifeCycle.end(rc);
            }
            // --------- /RequestLifeCycle End --------- //

            // Remove the requestContext from the threadLocal
            // NOTE: might want to do that after the closeSessionInView.
            requestContextTl.remove();

            // --------- Close HibernateSession --------- //
            if (hibernateSessionInViewHandler != null) {
                hibernateSessionInViewHandler.closeSessionInView();
            }
            // --------- /Close HibernateSession --------- //

        }

    }

    // --------- Service Request --------- //
    private void serviceTemplate(RequestContext rc) throws Throwable {
        HttpServletRequest req = rc.getReq();
        HttpServletResponse res = rc.getRes();

        req.setCharacterEncoding(CHAR_ENCODING);
        Map rootModel = rc.getRootModel();

        rootModel.put(MODEL_KEY_REQUEST, ContextModelBuilder.buildRequestModel(rc));

        // TODO: needs to implement this
        /*
         * if (!ignoreTemplateNotFound && !webApplication.getPart(part.getPri()).getResourceFile().exists()) {
         * sendHttpError(rc, HttpServletResponse.SC_NOT_FOUND, null); return; }
         */

        res.setContentType("text/html;charset=" + CHAR_ENCODING);
        // if not cachable, then, set the appropriate headers.
        res.setHeader("Pragma", "No-cache");
        res.setHeader("Cache-Control", "no-cache,no-store,max-age=0");
        res.setDateHeader("Expires", 1);

        application.processTemplate(rc);

        rc.getWriter().close();

    }

    private void serviceJson(RequestContext rc) throws Throwable {
        HttpServletRequest req = rc.getReq();
        HttpServletResponse res = rc.getRes();

        /* --------- Set Headers --------- */
        req.setCharacterEncoding(CHAR_ENCODING);
        res.setContentType("application/json");

        // if not cachable, then, set the appropriate headers.
        res.setHeader("Pragma", "No-cache");
        res.setHeader("Cache-Control", "no-cache,no-store,max-age=0");
        res.setDateHeader("Expires", 1);
        /* --------- /Set Headers --------- */

        application.processJson(rc);

        rc.getWriter().close();
    }

    public void serviceFallback(RequestContext rc) throws Throwable {

        String contextPath = rc.getContextPath();
        String resourcePath = rc.getResourcePath();

        String href = new StringBuilder(contextPath).append(resourcePath).toString();

        if (webBundleManager.isWebBundle(resourcePath)) {
            String content = webBundleManager.getContent(resourcePath);
            StringReader reader = new StringReader(content);
            httpWriter.writeStringContent(rc, href, reader, false, null);
        } else {
            // First, see and process the eventual WebFileHandler
            boolean webFileProcessed = application.processWebFile(rc);

            // if not processed, then, default processing
            if (!webFileProcessed) {
                File resourceFile = pathFileResolver.resolve(resourcePath);
                if (resourceFile.exists()) {
                    boolean isCachable = isCachable(resourcePath);
                    httpWriter.writeFile(rc, resourceFile, isCachable, null);
                } else {
                    sendHttpError(rc, HttpServletResponse.SC_NOT_FOUND, null);
                }
            }

        }
    }

    // --------- /Service Request --------- //

    private void sendHttpError(RequestContext rc, int errorCode, String message) throws IOException {

        // if the response has already been committed, there's not much we can do about it at this point...just let it
        // go.
        // the one place where the response is likely to be committed already is if the exception causing the error
        // originates while processing a template. the template will usually have already output enough html so that
        // the container has already started writing back to the client.
        if (!rc.getRes().isCommitted()) {
            rc.getRes().sendError(errorCode, message);
        }
    }

    private void sendHttpRedirect(RequestContext rc, AbortWithHttpRedirectException e) throws IOException {

        // like above, there's not much we can do if the response has already been committed. in that case,
        // we'll just silently ignore the exception.
        HttpServletResponse response = rc.getRes();
        if (!response.isCommitted()) {
            response.setStatus(e.getRedirectCode());
            response.addHeader("Location", e.getLocation());
        }
    }

    
    /*
     * First the resourcePath by remove extension (.json or .ftl) and adding index if the path end with "/"
     */
    static private String fixTemplateAndJsonResourcePath(String resourcePath){
        String path = FileUtil.getFileNameAndExtension(resourcePath)[0];
        return path;
    }
    
    static Set cachableExtension = MapUtil.setIt(".css", ".js", ".png", ".gif", ".jpeg");

    /**
     * Return true if the content pointed by the pathInfo is static.<br>
     * Right now, just return true if there is no extension
     * 
     * @param path
     * @return
     */
    static private final boolean isTemplatePath(String path) {
        
        if (path.lastIndexOf('.') == -1 || path.endsWith(".ftl")){
            return true;
        }else{
            return false;
        }
    }

    static private final boolean isJsonPath(String path) {
        if (path.endsWith(".json")) {
            return true;
        } else {
            return false;
        }
    }

    static final private boolean isCachable(String pathInfo) {
        String ext = FileUtil.getFileNameAndExtension(pathInfo)[1];
        return cachableExtension.contains(ext);
    }

    static final private String getLogErrorString(Throwable e) {
        StringBuilder errorSB = new StringBuilder();
        errorSB.append(e.getMessage());
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        errorSB.append("\n-- StackTrace:\n").append(sw.toString()).append("\n-- /StackTrace");
        return errorSB.toString();
    }

}
