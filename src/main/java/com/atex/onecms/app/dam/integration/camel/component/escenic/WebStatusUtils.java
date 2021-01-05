
package com.atex.onecms.app.dam.integration.camel.component.escenic;

import java.util.Iterator;
import java.util.List;

import com.atex.onecms.app.dam.workflow.WFStatusBean;
import com.atex.onecms.app.dam.workflow.WFStatusListBean;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Subject;

public class WebStatusUtils {
    ContentManager contentManager;
    private static final Subject SYSTEM_SUBJECT = new Subject("98", (String)null);

    public WebStatusUtils(ContentManager contentManager) {
        this.contentManager = contentManager;
    }


    public WFStatusBean getStatusById(String statusId) {
        ContentVersionId idStatusList = this.contentManager.resolve("atex.WebStatusList", SYSTEM_SUBJECT);
        ContentResult<WFStatusListBean> statusList = this.contentManager.get(idStatusList, WFStatusListBean.class, SYSTEM_SUBJECT);
        WFStatusListBean statusListBean = statusList.getContent().getContentData();
        List<WFStatusBean> statuses = statusListBean.getStatus();
        Iterator statusBeanIterator = statuses.iterator();

        WFStatusBean wfStatusBean;
        do {
            if (!statusBeanIterator.hasNext()) {
                return null;
            }

            wfStatusBean = (WFStatusBean)statusBeanIterator.next();
        } while(!wfStatusBean.getStatusID().equals(statusId));

        return wfStatusBean;
    }
}
