package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 *
 * @author jakub
 */
@XmlRootElement(name="feed")
public class Feed {

	private List<Link> link;
	private List<Entry> entry;
	private String totalResults;
	private String itemsPerPage;

	private List<Group> group;


	private String selfPageNumber;
	private String nextPageNumber;
	private String firstPageNumber;
	private String lastPageNumber;

	private String selfItemCount;
	private String nextItemCount;
	private String firstItemCount;
	private String lastItemCount;

	public String getSelfItemCount() {
		return selfItemCount;
	}

	public void setSelfItemCount(String selfItemCount) {
		this.selfItemCount = selfItemCount;
	}

	public String getNextItemCount() {
		return nextItemCount;
	}

	public void setNextItemCount(String nextItemCount) {
		this.nextItemCount = nextItemCount;
	}

	public String getFirstItemCount() {
		return firstItemCount;
	}

	public void setFirstItemCount(String firstItemCount) {
		this.firstItemCount = firstItemCount;
	}

	public String getLastItemCount() {
		return lastItemCount;
	}

	public void setLastItemCount(String lastItemCount) {
		this.lastItemCount = lastItemCount;
	}

	public String getNextPageNumber() {
		return nextPageNumber;
	}

	public void setNextPageNumber(String nextPageNumber) {
		this.nextPageNumber = nextPageNumber;
	}

	public String getFirstPageNumber() {
		return firstPageNumber;
	}

	public void setFirstPageNumber(String firstPageNumber) {
		this.firstPageNumber = firstPageNumber;
	}

	public String getLastPageNumber() {
		return lastPageNumber;
	}

	public void setLastPageNumber(String lastPageNumber) {
		this.lastPageNumber = lastPageNumber;
	}

	public String getSelfPageNumber() {
		return selfPageNumber;
	}

	public void setSelfPageNumber(String selfPageNumber) {
		this.selfPageNumber = selfPageNumber;
	}

	public List<Link> getLink() {
		return link;
	}

	public void setLink(List<Link> link) {
		this.link = link;
	}

	@XmlElement(name = "itemsPerPage", namespace="http://a9.com/-/spec/opensearch/1.1/")
	public String getItemsPerPage() {
		return itemsPerPage;
	}

	public void setItemsPerPage(String itemsPerPage) {
		this.itemsPerPage = itemsPerPage;
	}

	@XmlElement(name = "Group", namespace="http://xmlns.escenic.com/2015/facet")
	public List<Group> getGroup() {
		return group;
	}

	public void setGroup(List<Group> group) {
		this.group = group;
	}

	@XmlElement(name = "totalResults", namespace="http://a9.com/-/spec/opensearch/1.1/")
	public String getTotalResults() {
		return totalResults;
	}

	public void setTotalResults(String totalResults) {
		this.totalResults = totalResults;
	}

	@XmlElement(name="entry")
	public List<Entry> getEntry() {
		return entry;
	}

	public void setEntry(List<Entry> entry) {
		this.entry = entry;
	}
}
