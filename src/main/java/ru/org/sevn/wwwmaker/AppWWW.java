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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;

import org.apache.velocity.app.VelocityEngine;
import org.json.JSONException;
import org.json.JSONObject;

import ru.org.sevn.templ.ClasspathVelocityEngine;

public class AppWWW {
	
	public static void main(String[] args) throws UnsupportedEncodingException, IOException, JSONException {
		File config = new File(".AppWWW");
		if (config.exists() && config.canRead()) {
			String fileStr = new String(Files.readAllBytes(config.toPath()), "UTF-8");
			JSONObject jobj = new JSONObject(fileStr);
			boolean force = false;
			String logo = "logo.png";
			String favico = "logo.ico";
			String css = "css.css";
			String resdir = null;
			if (jobj.has("resdir")) {
				resdir = jobj.getString("resdir");
			}
			if (jobj.has("force")) {
				force = jobj.getBoolean("force");
			}
			if (jobj.has("logo")) {
				logo = jobj.getString("logo");
			}
			if (jobj.has("favico")) {
				favico = jobj.getString("favico");
			}
			if (jobj.has("css")) {
				css = jobj.getString("css");
			}
			String dirName = jobj.getString("dir");
			File dirr = new File(dirName);
			if (dirr.exists() && dirr.canRead()) {
				Menu root = WWWGenerator.makeMenu(".", dirr, null);
				VelocityEngine ve = new VelocityEngine();
				if (resdir == null || !new File(resdir).exists()) {
					ClasspathVelocityEngine.applyClasspathResourceLoader(ve);
				} else {
					ClasspathVelocityEngine.applyFileResourceLoader(ve, resdir);
					
				}

				WWWGenerator gen = new WWWGenerator(
						new File(dirr, css), 
						new File(dirr, logo),
						new File(dirr, favico), 
						ve
						);
				gen.setForce(force);
				gen.fillMenu(root, false);
				gen.fillMenu(root, !false);
			} else {
				System.out.println("Can't process dir " + dirr.getAbsolutePath());
			}
		} else {
			System.out.println("Can't read from " + config.getAbsolutePath());
		}
	}
}
