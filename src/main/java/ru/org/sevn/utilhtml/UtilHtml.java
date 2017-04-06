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
package ru.org.sevn.utilhtml;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;

public class UtilHtml {
	public static String getCleanHtmlBodyContent(String html) {
		if (html == null) return null;
		return getHtmlBodyContent(Jsoup.clean(html, Whitelist.basic()));
	}
	public static String getHtmlBodyContent(String html) {
		if (html == null) return null;
		
		Document doc = Jsoup.parseBodyFragment(html);
//		Document doc = Jsoup.parse(html);
		if (doc != null) {
			return doc.body().html();
		}
		return null;
	}
}
