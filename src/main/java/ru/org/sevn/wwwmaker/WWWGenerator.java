/*******************************************************************************
 * Copyright 2017 Veronica Anokhina.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package ru.org.sevn.wwwmaker;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jcodec.api.awt.FrameGrab;
import org.jcodec.common.FileChannelWrapper;
import org.jcodec.common.NIOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import ru.org.sevn.rss.Rss;
import ru.org.sevn.rss.RssChanel;
import ru.org.sevn.util.ComplexFilenameFilter;
import ru.org.sevn.util.DirNotHiddenFilenameFilter;
import ru.org.sevn.util.FileNameComparator;
import ru.org.sevn.util.FileUtil;
import ru.org.sevn.util.NotFilenameFilter;
import ru.org.sevn.util.StartWithFilenameFilter;
import ru.org.sevn.utilhtml.UtilHtml;
import ru.org.sevn.utilwt.ImageUtil;

public class WWWGenerator {
	private boolean force = !true;
        private boolean useThumb = true;
	public static final String[] ICONS_EXT = new String[] {".png", ".jpg", ".jpeg"};
	public static final String[] TXT_EXT = new String[] {".txt"};

	private VelocityEngine ve = new VelocityEngine();
	private File logoFile;
	private File cssFile;
	private File faviconFile;
	
	private FileNameComparator ASC = new FileNameComparator();
	private FileNameComparator DSC = new FileNameComparator(false);
	private DirNotHiddenFilenameFilter dirFileFilter = new DirNotHiddenFilenameFilter();
	
	private Rss rss;
	
	public WWWGenerator(File cssFile, File logoFile, File ficon, VelocityEngine ve) {
		this.ve = ve;
		ve.init();
		this.cssFile = cssFile;
		this.logoFile = logoFile;
		this.faviconFile = ficon;
		rss = new Rss();
		RssChanel chanel = new RssChanel();
		rss.addChanel(chanel);
	}
	
	public Comparator<File> getFileComparator(String order) {
		if (order != null) {
			if (order.toLowerCase().startsWith("asc")) {
			} else {
				return DSC;
			}
		}
		return ASC;
	}
	private static File[] sort(File[] fileArr, Comparator<File> comparator) {
		Arrays.sort(fileArr, comparator);
		return fileArr;
	}
	static class HtmlContent {
		private StringBuilder content = new StringBuilder();
		private File file;
		private final Menu root;
		public HtmlContent(Menu f) {
			root = f;
		}
	}
	
	private String appendContent(HtmlContent pageContent, String content, String lastContent, String contentType) {
		if ("img".equals(contentType) &&  !"img".equals(lastContent)) {
			pageContent.content.append("<div class='photos'>");
		}
		if ("img".equals(lastContent) && ! "img".equals(contentType)) {
			pageContent.content.append("</div>");
		}
		if ("vid".equals(contentType) &&  !"vid".equals(lastContent)) {
			pageContent.content.append("<div class='videos'>");
		}
		if ("vid".equals(lastContent) && ! "vid".equals(contentType)) {
			pageContent.content.append("</div>");
		}
		pageContent.content.append(content);
		if ("text".equals(contentType)) {
			pageContent.content.append("<br>");
		}
		return contentType;
	}
	public HtmlContent fillMenu(Menu root, boolean writeContent) {
		Comparator<File> comparator = getFileComparator(root.getDirProperties().getOrder());
		
		HtmlContent content = null;
		HtmlContent subcontent = null;
		if (!writeContent) {
			for(File f : sort(root.getFile().listFiles(dirFileFilter), comparator)) { 
				if (f.isDirectory()) {
					root.addMenu(makeMenu(null, f, root.getDirProperties()));
				}
			}
		}
		for(Menu m : root.getMenus()) {
			HtmlContent cnt = fillMenu(m, writeContent);
			if (subcontent == null) {
				subcontent = cnt;
			}
		}
		
		int contentCnt = 0;
		int contentCntImg = 0;
		int contentCntVid = 0;
		FilenameFilter contentFilenameFilter = new ComplexFilenameFilter(
                new StartWithFilenameFilter("content", ".html"),
                new StartWithFilenameFilter("_content", ".html")
        );
		FilenameFilter imgFilenameFilter = new StartWithFilenameFilter("img-", ".jpg");
		FilenameFilter vidFilenameFilter = new StartWithFilenameFilter("vid-", ".mp4");
		File[] files = sort(root.getFile().listFiles(
				new ComplexFilenameFilter(
						contentFilenameFilter,
						imgFilenameFilter,
						vidFilenameFilter
						)
			), comparator);
		int pages = files.length / root.getDirProperties().getContentCntMax();
//		if (files.length % contentCntMax > 0) {
//			pages++;
//		}
		JSONArray imgFiles = null;
		HashMap<String, Integer> imgFilesMap = new HashMap<>(); 
		if (writeContent) {
			File[] filesImg = sort(root.getFile().listFiles(
					new ComplexFilenameFilter(
							imgFilenameFilter,
							vidFilenameFilter
							)
				), comparator);
			imgFiles = new JSONArray();
			int imgFilesIdx = -1;
			for (File f: filesImg) {
				JSONObject jobj = new JSONObject();
				imgFiles.put(jobj);
				imgFilesIdx++;
				String imgComment = "&nbsp;";
				if (imgFilenameFilter.accept(f.getParentFile(), f.getName())) {
					try {
						imgComment = UtilHtml.getCleanHtmlBodyContent(ImageUtil.getImageUserCommentString(f, "UTF-8"));
						if (imgComment == null) {
							imgComment = "&nbsp;";
						}
					} catch (UnsupportedEncodingException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					jobj.put("tp", 1); 
				} else {
					jobj.put("tp", 2); 
				}
				jobj.put("comment", imgComment);
				jobj.put("name", f.getName());
				imgFilesMap.put(f.getName(), imgFilesIdx);
			}
			writeJs(root.getFile(), imgFiles);
		}
		int page = 0;
		for(File f : files) {
			if (imgFilenameFilter.accept(f.getParentFile(), f.getName())) {
				contentCntImg++;
			} else if (contentFilenameFilter.accept(f.getParentFile(), f.getName())) {
				contentCnt++;
			} else if (vidFilenameFilter.accept(f.getParentFile(), f.getName())) {
				contentCntImg++; // ?????
			}
			if (contentCnt >= root.getDirProperties().getContentCntMax() || 
				contentCntImg >= root.getDirProperties().getContentCntMaxImg() ||
				contentCntVid >= root.getDirProperties().getContentCntMaxVid()
				) {
				
				contentCnt = 0;
				contentCntImg = 0;
				contentCntVid = 0;
				page++;
			}
		}
		if (contentCnt == 0 && contentCntImg == 0 && contentCntVid == 0) {
		} else {
			pages = page + 1;
		}
		
		page = 0;
		contentCnt = 0;
		contentCntImg = 0;
		contentCntVid = 0;
		HtmlContent pageContent = null;
		String lastContent = null;
		for(File f : files) {
			
			if (content == null) {
				pageContent = new HtmlContent(root);
				pageContent.file = new File(root.getFile(), "index.html");
				content = pageContent;
				if (!writeContent) {
					break;
				}
				if(!root.getDirProperties().isModify()) {
					if (!force) {
						return applyContent(root, content);
					}
				}
				if (pages > 1) {
					lastContent = appendContent(pageContent, pagination(page, pages, root.getFile()), lastContent, "page");
				}
			}
			if (writeContent) {
				if (imgFilenameFilter.accept(f.getParentFile(), f.getName())) {
					contentCntImg++;
					lastContent = appendContent(pageContent, getImgHref(root.getFile(), f, imgFilesMap, imgFiles), lastContent, "img");
				} else
				if (contentFilenameFilter.accept(f.getParentFile(), f.getName())) {
					contentCnt++;
					lastContent = appendContent(pageContent, getText(f), lastContent, "text");
				} else
				if (vidFilenameFilter.accept(f.getParentFile(), f.getName())) {
					contentCntImg++; //?????????
					lastContent = appendContent(pageContent, getVidHref(root.getFile(), f, imgFilesMap), lastContent, "img");
				}
				if (contentCnt >= root.getDirProperties().getContentCntMax() || 
					contentCntImg >= root.getDirProperties().getContentCntMaxImg() ||
					contentCntVid >= root.getDirProperties().getContentCntMaxVid() 
					) {
					
					contentCnt = 0;
					contentCntImg = 0;
					contentCntVid = 0;

					lastContent = appendContent(pageContent, pagination(page, pages, root.getFile()), lastContent, "page");
					if (content != pageContent) {
						write(pageContent, imgFiles);
					}
					page++;
					pageContent = new HtmlContent(root);
					pageContent.file = new File(root.getFile(), "index" + (page+1) + ".html");
					lastContent = appendContent(pageContent, pagination(page, pages, root.getFile()), lastContent, "page");
				}
			}
		}
		if (writeContent) {
			if (pageContent != null && content != pageContent) {
				lastContent = appendContent(pageContent, pagination(page, pages, root.getFile()), lastContent, "page");
				write(pageContent, imgFiles);
			}
			if (content != null) {
				write(content, imgFiles);
			}
		}
		HtmlContent emptyContent = null;
		if (content == null) {
			emptyContent = new HtmlContent(root);
			emptyContent.file = new File(root.getFile(), "index.html");
			for (Menu m : root.getMenus()) {
				File f = m.getContentFile();
				if (f == null) {
					f = m.getFile();
				}
				appendContent(emptyContent, getHref(root.getFile(), f, m.getAnyTitle()), "text", "text");
			}
		}
		if (content == null && subcontent != null) {
			content = subcontent;
		}
		if (content == null) {
			content = emptyContent;
			if(!root.getDirProperties().isModify()) {
				if (!force) {
					return applyContent(root, content);
				}
			}
		}
		if (writeContent && emptyContent != null) {
			lastContent = appendContent(emptyContent, "\n", null, "text");
			write(emptyContent, imgFiles);
		}
		//TODO
		// up menu
		// selected index - the first
		
		//root.getMenus().clear();
		return applyContent(root, content);
	}
	private HtmlContent applyContent(Menu root, HtmlContent content) {
		root.setContentFile(content.file);
		return content;
	}
	private String getHref(File root, File img, String title) {
		Template templ = ve.getTemplate("linkTempl.html");
		StringWriter writer = new StringWriter();
		VelocityContext context = new VelocityContext();
		context.put("href", FileUtil.getRelativePath(root, img));
		context.put("title", title);
		templ.merge(context, writer);
		return writer.toString();
	}
	private String getVidHref(File root, File img, HashMap<String, Integer> imgFilesMap) {
		Template templ = ve.getTemplate("videoTempl.html");
		StringWriter writer = new StringWriter();
		VelocityContext context = new VelocityContext();
        File thumbimg = new File(img.getAbsolutePath() + ".png");
        makeThumbs(new FileVideoIconSupplier(img), thumbimg, 160);
		context.put("href", FileUtil.getRelativePath(root, img));
		context.put("hrefidx", imgFilesMap.get(img.getName()));
		templ.merge(context, writer);
		return writer.toString();
	}
    
    public static class FileVideoIconSupplier implements ImageIconSupplier {
        private final File img;
        private ImageIcon imageIcon;
        public FileVideoIconSupplier(File fl) {
            this.img = fl;
        }

        @Override
        public ImageIcon getImageIcon() {
            if (imageIcon == null) {
                try {
                    FileChannelWrapper grabch = NIOUtils.readableFileChannel(img);
                    BufferedImage frame = null;
                    try { 
                        FrameGrab grab = new FrameGrab(grabch);
                        for (int i = 0; i < 50; i++) {
                            grab.seekToFrameSloppy(50);
                            try {
                                frame = grab.getFrame();
                            } catch (Exception e) {
                                Logger.getLogger(WWWGenerator.class.getName()).log(Level.SEVERE, null, e);
                            }
                        }
                    } finally {
                        NIOUtils.closeQuietly(grabch);
                    }
                    if (frame != null) {
                        imageIcon = new ImageIcon(frame);
                    }
                } catch (Exception ex) {
                    Logger.getLogger(FileVideoIconSupplier.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            return imageIcon;
        }
    }
    
    public static class FileImageIconSupplier implements ImageIconSupplier {
        private final File img;
        private ImageIcon imageIcon;
        public FileImageIconSupplier(File fl) {
            this.img = fl;
        }

        @Override
        public ImageIcon getImageIcon() {
            if (imageIcon == null) {
                imageIcon = new ImageIcon(img.getPath());
            }
            return imageIcon;
        }
    }
    
    public static interface ImageIconSupplier {
        ImageIcon getImageIcon();
    }
    private static File makeThumbs(ImageIconSupplier imgSupplier, File thumbimg, int height) {
        //BufferedImage frame = FrameGrab.getFrame(new File("/Users/jovi/Movies/test.mp4"), i);
        File ret = null;
        if (!thumbimg.exists()) {
            File thumbdir = thumbimg.getParentFile();
            if (!thumbdir.exists()) {
                thumbdir.mkdirs();
            }
            
            System.err.println("generate thumb>>>" + thumbimg);
            try {
                ImageIcon ii = ImageUtil.getScaledImageIconHeight(imgSupplier.getImageIcon(), height, false);
                ImageIO.write(ImageUtil.getBufferedImage(ii), "png", thumbimg);
                ret = thumbimg;
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            ret = thumbimg;
        }
        return ret;
    }
    private String getImgHref(File root, File img, HashMap<String, Integer> imgFilesMap, JSONArray imgFiles) {
		Template templ = ve.getTemplate("imgTempl.html");
		StringWriter writer = new StringWriter();
		VelocityContext context = new VelocityContext();
		
		context.put("href", FileUtil.getRelativePath(root, img));
		context.put("hrefidx", imgFilesMap.get(img.getName()));
		
        context.put("useThumb", useThumb);
        try {
            String imgComment = imgFiles.getJSONObject(imgFilesMap.get(img.getName())).getString("comment");
            context.put("imgComment", imgComment);
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        if (useThumb) {
            FileImageIconSupplier imgsupl = new FileImageIconSupplier(img);
            File thumbimg = new File(new File(img.getParentFile(), ".thumb"), img.getName() + ".png");
            makeThumbs(imgsupl, thumbimg, 160);
            File thumbimgBig = new File(new File(img.getParentFile(), ".thumbg"), img.getName() + ".png");
            makeThumbs(imgsupl, thumbimgBig, 736);
            context.put("hrefthumb", FileUtil.getRelativePath(root, thumbimg));
        } else {
            context.put("hrefthumb", FileUtil.getRelativePath(root, img));
        }
		templ.merge(context, writer);
		return writer.toString();
	}
	private String pagination(int page, int pages, File dir) {
		StringWriter writer = new StringWriter();
		if (pages > 1) {
			Template nav = ve.getTemplate("paginationTempl.html");
			VelocityContext context = new VelocityContext();
			context.put("pages", paginationItems(page, pages, dir));
			nav.merge(context, writer);
		}
		return writer.toString();
	}
	private String paginationItems(int page, int pages, File dir) {
		//File file2 = new File(dir, "index"+)
		StringWriter writer = new StringWriter();
		Template nav = ve.getTemplate("paginationItemTempl.html");
		
		if (pages > 0) {
			int li, ri;
			if (pages <= 10) {
				li = -1;
				ri = pages;
			} else {
				li = page - 3 - 1;
				if (li < 0) {
					li = 0;
				}
				ri = li + 5;
			}
			
			for (int i = 0; i < pages; i++) {
				String is = "";
				if (i != 0) {
					is = "" + (i+1);
				}
				VelocityContext context = new VelocityContext();
				if (i != page) {
					context.put("href", "index" + is + ".html");
				}
				context.put("title", "" + (i+1));
				nav.merge(context, writer);
				writer.flush();
				if (i < li) {
					i = li;
					writer.append("...");
				} else if (i > ri && i < pages - 1) {
					i = pages - 2;
					writer.append("...");
				}
			}				
		}
		return writer.toString();
	}
	private String applyTemplate(Template nav, HtmlContent content, Menu m) {
		String title = m.getTitle();
		if (title == null) {
			title = m.getId();
		}
		return applyTemplate(nav, content, m, title, FileUtil.getRelativePath(content.file, m.getContentFile()));
	}
	private String applyTemplateDir(Template nav, HtmlContent content, Menu m) {
		return applyTemplate(nav, content, m, "&uArr;", FileUtil.getRelativePath(content.file, new File(m.getFile(), "index.html")));
	}
	private String applyTemplate(Template nav, HtmlContent content, Menu m, String title, String href) {
		StringWriter writer = new StringWriter();
		VelocityContext context = new VelocityContext();
		context.put("href", href);
		context.put("title", title);
		context.put("iconname", m.getIconPath());
		nav.merge(context, writer);
		return writer.toString();
	}
	private HtmlContent appendMenus(HtmlContent content, VelocityContext context) {
		Menu mainMenu = Menu.getRoot(content.root);
		{
			Template nav = ve.getTemplate("navcolumnTempl.html");
			StringBuilder str = new StringBuilder();
			for (Menu m : mainMenu.getMenus()) {
				//System.out.println("++++++++++++"+m.getId()+":"+content.root.hasParent(m)+":"+content.root.getId()+":"+getHref(content.root.getFile(), m.getContentFile()));
				str.append(applyTemplate(nav, content, m)).append("\n");
			}
			context.put("navcolumns", str.toString());
		}
		Menu parent = null;
		if (content.root != null) {
			parent = content.root.getParent();
		}
		if (parent != null && mainMenu != parent) {
			Template subnav = ve.getTemplate("subnavcolumnTempl.html");
			StringBuilder str = new StringBuilder();
			if (parent.getMenus().size() > 0 && parent.getLevel() > 1) {
				str.append(applyTemplateDir(subnav, content, parent)).append("\n");
			}
			for(Menu m : parent.getMenus()) {
				//System.out.println("----------"+m.getId()+/*":"+m.getContentFile()+*/":"+getHref(content.root.getFile(), m.getContentFile()));
				str.append(applyTemplate(subnav, content, m)).append("\n");
			}			
			context.put("subnavcolumns", str.toString());
		}
		return content;
	}
	private String makeBreadcrumbs(final Menu m, final String prevpath) {
		if (m == null) return "";
		Menu parent = m.getParent();
		if (parent == null) {
			return "";
		}
		String path = "";
		if (prevpath != null) {
			path = prevpath;
		}
		StringWriter writer = new StringWriter();
		writer.append(makeBreadcrumbs(parent, path + "../"));
		
		Template template = ve.getTemplate("breadcrumbTempl.html");
		VelocityContext context = new VelocityContext();
		context.put("title", m.getAnyTitle());
		if (prevpath != null) {
			context.put("href", path + "index.html");
		} else {
			context.put("hrefcur", path);
        }
		
		template.merge(context, writer);
		
		return writer.toString();
	}
	private void writeJs(File dir, JSONArray imgFiles) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
					new File(dir, "index.js")
					), "UTF-8"));
			try {
				writer.append("var imgFiles=").append(imgFiles.toString(2)).append(";");
			} finally {
				writer.close();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	private void write(HtmlContent content, JSONArray imgFiles) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(content.file), "UTF-8"));
		} catch (UnsupportedEncodingException | FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println(">>>>>>>>>>>"+content.file);
		Template template = ve.getTemplate("indexTempl.html");
		VelocityContext context = new VelocityContext();
        context.put("useThumb", useThumb);

		appendMenus(content, context);
		context.put("fakeimg", FileUtil.getRelativePath(content.file, logoFile)); 
		
		context.put("pageContent", content.content.toString());
		context.put("breadcrumbs", makeBreadcrumbs(content.root, null));
		context.put("title", content.root.getFullTitle());
		
		context.put("cssName", FileUtil.getRelativePath(content.file, cssFile));
		
		Menu mainMenu = Menu.getRoot(content.root);
		if (mainMenu.getContentFile() != null) {
			context.put("navLogoHref", FileUtil.getRelativePath(content.file, mainMenu.getContentFile() ));
		} else
		if (mainMenu.getMenus().size() > 0) {
			Menu mainMenuFirst = mainMenu.getMenus().get(0);
			context.put("navLogoHref", FileUtil.getRelativePath(content.file, mainMenuFirst.getContentFile() ));
		}
		if (logoFile.exists()) {
			context.put("navLogo", FileUtil.getRelativePath(content.file, logoFile));
		}
		context.put("favicon", FileUtil.getRelativePath(content.file, faviconFile));
		if (writer != null) {
			template.merge(context, writer);
			try {
				writer.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		content.content = null;
	}
	
	public static Menu makeMenu (String id, File file, DirProperties dp) {
		if (id == null) {
			id = file.getName();
		}
		Menu m = new Menu().setId(id).setFile(file);
		if (dp != null) {
			m.setDirProperties(dp.clone());
		}
		File indexFile = new File(file, "index.html");
		if (indexFile.exists()) {
			if (indexFile.lastModified() < file.lastModified()) {
				m.getDirProperties().setModify(true);
			} else {
				for (File f : file.listFiles(new NotFilenameFilter("index"))) {
					if (indexFile.lastModified() < f.lastModified()) {
						//System.err.println("+++++++"+f+":"+indexFile);
						m.getDirProperties().setModify(true);
						break;
					}
				}
				
			}
		} else {
			m.getDirProperties().setModify(true);
		}
		System.err.println("---- upd--------"+m.getDirProperties().isModify() + ":" + file);
		
		m.setIconPath(getExists(file, ".icon", ICONS_EXT));
		m.setTitle(getText(FileUtil.getExistsFile(file, ".title", TXT_EXT)));
		m.setDescription(getText(FileUtil.getExistsFile(file, ".description", TXT_EXT)));
		m.setSinglePage(getExists(file, ".single", TXT_EXT) != null);
		
		m.getDirProperties().setOrder(getText(
				FileUtil.getExistsFile(file, ".order", TXT_EXT),
				m.getDirProperties().getOrder()));
		
		m.getDirProperties().setContentCntMax(
				getTextInt(
						FileUtil.getExistsFile(file, ".contentCntMax", TXT_EXT),
						m.getDirProperties().getContentCntMax()
						));
		m.getDirProperties().setContentCntMaxImg(
				getTextInt(
						FileUtil.getExistsFile(file, ".contentCntMaxImg", TXT_EXT),
						m.getDirProperties().getContentCntMaxImg()
						));
		return m;
	}
	
	public static String getText (File f, String name, String ... extensions) {
		File fl = FileUtil.getExistsFile(f, name, extensions);
		if (fl != null) {
			getText(fl);
		}
		return null;
	}
	public static String getText(File f) {
		return getText(f, null);
	}
	public static int getTextInt(File f, int defVal) {
		String s = getText(f);
		//System.err.println("getTextInt>"+defVal+":"+s+":"+f);
		if (s != null) {
			try {
				int ret = Integer.parseInt(s);
				if (ret > 2 && ret < 1000) {
					return ret;
				}
			} catch (Exception e) {}
		}
		return defVal;
	}
	public static String getText(File f, String defVal) {
		if (f != null) {
			try {
				return new String(Files.readAllBytes(f.toPath()), "UTF-8");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return defVal;
	}
	public static String getExists (File f, String name, String ... extensions) {
		File fl = FileUtil.getExistsFile(f, name, extensions);
		if (fl != null) {
			return fl.getName();
		}
		return null;
	}

	public boolean isForce() {
		return force;
	}

	public void setForce(boolean force) {
		this.force = force;
	}

    public boolean isUseThumb() {
        return useThumb;
    }

    public void setUseThumb(boolean useThumb) {
        this.useThumb = useThumb;
    }
	
}
