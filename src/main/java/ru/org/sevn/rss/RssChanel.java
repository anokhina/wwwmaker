package ru.org.sevn.rss;

import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class RssChanel {
	
	@XmlElement(required=true)
	public String title;
	@XmlElement(required=true)
	public String link; //TODO
	@XmlElement(required=true)
	public String description;
	
	@XmlElement
	public String language = "ru";
	@XmlElement
	public String copyright;
	@XmlElement
	public String managingEditor;
	@XmlElement
	public String webMaster;
	
	private Date pubDate = new Date();
	private Date lastBuildDate = new Date();
	private List<RssCategory> category;
	private String generator;
	private String docs = "http://www.rssboard.org/rss-specification";
	private RssCloud cloud;
	private int ttl = 60;
	private RssImage image;
	
	
	
}
