package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import com.atex.onecms.app.dam.integration.camel.component.escenic.HrefAdapter;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Objects;
/**
 *
 * @author adamgiles
 */
@XmlRootElement
public class Origin {

    private String href;

    public Origin() {
    }

    public void setHref(String href) {
        this.href = href;
    }

    @XmlAttribute(name = "href")
    @XmlJavaTypeAdapter(value= HrefAdapter.class)
    public String getHref() {
        return href;
    }

}
