package org.snowfk.web.renderer;

import java.io.Writer;

public interface TemplateRenderer{
    
    public void render(String path, Object model, Writer out);
    
}