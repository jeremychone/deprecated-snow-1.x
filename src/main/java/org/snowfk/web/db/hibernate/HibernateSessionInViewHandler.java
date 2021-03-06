package org.snowfk.web.db.hibernate;

public interface HibernateSessionInViewHandler {

    public void openSessionInView();
    
    public void afterActionProcessing();
    
    public void closeSessionInView();
}
