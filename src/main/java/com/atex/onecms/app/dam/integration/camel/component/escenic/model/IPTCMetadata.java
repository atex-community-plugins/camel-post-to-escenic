package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegPhotoshopMetadata;
import org.apache.commons.imaging.formats.jpeg.iptc.IptcRecord;
import org.apache.commons.imaging.formats.jpeg.iptc.IptcType;
import org.apache.commons.imaging.formats.jpeg.iptc.IptcTypes;
import org.apache.commons.imaging.formats.jpeg.iptc.PhotoshopApp13Data;

public class IPTCMetadata {

	private PhotoshopApp13Data m_photoshopApp13Data;

	private String m_title;

	private String m_author;

	private String m_contact;

	private String m_extendedAuthor;

	private String m_copyrights;

	private String m_usage;

	private String m_description;

	private String m_synopsis;

	private String m_editor;

	private List<String> m_keywords;

	private String m_city;

	private String m_country;

	private String m_state;

	private String m_creationDate;

	private String m_source;

	private boolean m_marked;

	private String m_webStatement;
	private String xmp;


	public String getTitle() {
		return m_title;
	}

	public void setTitle(String title) {
		this.m_title = title;
	}

	public String getAuthor() {
		return m_author;
	}

	public void setAuthor(String author) {
		this.m_author = author;
	}

	public String getExtendedAuthor() {
		return m_extendedAuthor;
	}

	public void setExtendedAuthor(String extendedAuthor) {
		this.m_extendedAuthor = extendedAuthor;
	}

	public String getCopyright() {
		return m_copyrights;
	}

	public void setCopyright(String copyrights) {
		this.m_copyrights = copyrights;
		if (m_copyrights != null  && m_copyrights.length() > 0) {
			setMarked(true);
		}
	}

	public String getUsage() {
		return m_usage;
	}

	public void setUsage(String usage) {
		this.m_usage = usage;
	}

	public String getDescription() {
		return m_description;
	}

	public void setDescription(String description) {
		this.m_description = description;
	}

	public String getSynopsis() {
		return m_synopsis;
	}

	public void setSynopsis(String caption) {
		this.m_synopsis = caption;
	}

	public String getEditor() {
		return m_editor;
	}

	public void setEditor(String editor) {
		this.m_editor = editor;
	}

	public List<String> getKeywords() {
		return m_keywords;
	}

	public void setKeywords(List<String> keywords) {
		this.m_keywords = keywords;
	}

	public String getCity() {
		return m_city;
	}

	public void setCity(String city) {
		this.m_city = city;
	}

	public String getCountry() {
		return m_country;
	}

	public void setCountry(String country) {
		this.m_country = country;
	}

	public String getState() {
		return m_state;
	}

	public void setState(String state) {
		this.m_state = state;
	}

	public String getCreationDate() {
		return m_creationDate;
	}

	public void setCreationDate(String date) {
		this.m_creationDate = date;
	}

	public void setMarked(boolean marked) {
		this.m_marked = marked;
	}

	public boolean isMarked() {
		return m_marked;
	}

	public void setSource(String source) {
		this.m_source = source;
	}

	public String getSource() {
		return m_source;
	}

	public void setWebStatement(String web) {
		this.m_webStatement = web;
	}

	public String getWebStatement() {
		return m_webStatement;
	}

	public void setContact(String contact) {
		this.m_contact = contact;
	}

	public String getContact() {
		return m_contact;
	}

	public IPTCMetadata() {
		m_photoshopApp13Data = null;
	}

	public IPTCMetadata(ImageMetadata iptc) {
		super();
		if (iptc == null) {
			return;
		}

		JpegPhotoshopMetadata psMetadata = ((JpegImageMetadata) iptc).getPhotoshop();
		if (psMetadata != null) {
			m_photoshopApp13Data = psMetadata.photoshopApp13Data;


			setTitle(getValue(IptcTypes.OBJECT_NAME));
			setAuthor(getValue(IptcTypes.BYLINE));
			setCity(getValue(IptcTypes.CITY));
			setCopyright(getValue(IptcTypes.COPYRIGHT_NOTICE));
			setCountry(getValue(IptcTypes.COUNTRY_PRIMARY_LOCATION_NAME));
			setDescription(getValue(IptcTypes.CAPTION_ABSTRACT));
			setEditor(getValue(IptcTypes.WRITER_EDITOR));
			setExtendedAuthor(getValue(IptcTypes.BYLINE_TITLE));
			setKeywords(getValues(IptcTypes.KEYWORDS));
			setState(getValue(IptcTypes.PROVINCE_STATE));
			setTitle(getValue(IptcTypes.HEADLINE));
			setUsage(getValue(IptcTypes.SPECIAL_INSTRUCTIONS));
			setSource(getValue(IptcTypes.SOURCE));
			setCreationDate(getValue(IptcTypes.DATE_CREATED));
			setContact(getValue(IptcTypes.CONTACT));

		} else {
			m_photoshopApp13Data = null;
		}
	}

	public PhotoshopApp13Data getPhotoshopApp13Data() {
		List<IptcRecord> records = new ArrayList<IptcRecord>();

		// First check if we had IPTC metadata already
		if (m_photoshopApp13Data != null) {
			records = m_photoshopApp13Data.getRecords();
		}

		setRecord(records, IptcTypes.OBJECT_NAME, getTitle());
		setRecord(records, IptcTypes.BYLINE, getAuthor());
		setRecord(records, IptcTypes.CITY, getCity());
		setRecord(records, IptcTypes.COPYRIGHT_NOTICE, getCopyright());
		setRecord(records, IptcTypes.COUNTRY_PRIMARY_LOCATION_NAME, getCountry());
		setRecord(records, IptcTypes.CAPTION_ABSTRACT, getDescription());
		setRecord(records, IptcTypes.WRITER_EDITOR, getEditor());
		setRecord(records, IptcTypes.BYLINE_TITLE, getExtendedAuthor());
		setRecord(records, IptcTypes.PROVINCE_STATE, getState());
		setRecord(records, IptcTypes.HEADLINE, getTitle());
		setRecord(records, IptcTypes.SPECIAL_INSTRUCTIONS, getUsage());
		setRecord(records, IptcTypes.DATE_CREATED, getCreationDate());
		setRecord(records, IptcTypes.SOURCE, getSource());
		setRecords(records, IptcTypes.KEYWORDS, getKeywords());
		setRecord(records, IptcTypes.CONTACT, getContact());

		PhotoshopApp13Data data = null;
		if (m_photoshopApp13Data != null) {
			data = new PhotoshopApp13Data(records, m_photoshopApp13Data.getNonIptcBlocks());
		} else {
			data = new PhotoshopApp13Data(records, new ArrayList(0));
		}
		m_photoshopApp13Data = data;

		return data;

	}

	public PhotoshopApp13Data getOriginalIPTCMetadata() {
		return m_photoshopApp13Data;
	}

	public void setRecord(List<IptcRecord> records, IptcType type, String value) {
		IptcRecord rec = getRecordByType(type.getType());
		if (value != null) {
			// As we can't change the value of a record, we remove the record, and recreate a new one
			if (rec != null) {
				records.remove(rec);
			}
			records.add(new IptcRecord(type, value));
		}
	}

	public void setRecords(List<IptcRecord> records, IptcType type, List<String> values) {
		List<IptcRecord> rec = getRecordsByType(type.getType());
		if (values != null) {
			if (rec != null) {
				records.removeAll(rec);
			}
			for (String s : values) {
				records.add(new IptcRecord(type, s));
			}
		}
	}

	public IptcRecord getRecordByType(int type) {
		if (m_photoshopApp13Data == null || m_photoshopApp13Data.getRecords() == null) {
			return null;
		}
		for (IptcRecord record : (List<IptcRecord>) m_photoshopApp13Data.getRecords()) {
			if (record.iptcType.getType() == type) {
				return record;
			}
		}
		return null;
	}

	public List<IptcRecord> getRecordsByType(int type) {
		if (m_photoshopApp13Data == null || m_photoshopApp13Data.getRecords() == null) {
			return null;
		}
		List<IptcRecord> list = new ArrayList<IptcRecord>();
		for (IptcRecord record : (List<IptcRecord>) m_photoshopApp13Data.getRecords()) {
			if (record.iptcType.getType() == type) {
				list.add(record);
			}
		}
		return list;
	}

	public String getValue(IptcType type) {
		IptcRecord record = getRecordByType(type.getType());
		if (record != null) {
			return record.getValue();
		} else {
			return null;
		}
	}

	public List<String> getValues(IptcType type) {
		List<IptcRecord> records = getRecordsByType(type.getType());
		List<String> values = new ArrayList<String>();
		if (records != null) {
			for (IptcRecord s : records) {
				values.add(s.getValue());
			}
		}
		return values;
	}

	public String getXmp() {
		return xmp;
	}
}
