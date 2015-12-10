/*
 * Copyright 2014, 2014 AuditMark
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the Lesser GPL
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.auditmark.jscrambler.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author magalhas
 */
public class JScramblerFacade {

  public static final int DELAY = 3;

  public static final String ZIP_TMP_FILE = ".tmp.zip";

  public static boolean silent = false;

  public static InputStream downloadCode(JScrambler client, String projectId) throws Exception {
    return JScramblerFacade.downloadCode(client, projectId, null);
  }

  public static InputStream downloadCode(JScrambler client, String projectId, String sourceId) throws Exception {
    JScramblerFacade.pollProject(client, projectId);
    if (!JScramblerFacade.silent) {
      System.out.println("Downloading project...");
    }
    String path = "/code/" + projectId;
    if (sourceId != null) {
      path += "/" + sourceId;
    } else {
      path += ".zip";
    }
    InputStream result = (InputStream) client.get(path);
    if (!JScramblerFacade.silent) {
      System.out.println("Project downloaded");
    }
    return result;
  }

  public static JSONObject getInfo(JScrambler client) throws JSONException {
    String response = (String) client.get("/code.json");
    JSONObject result = new JSONObject(response);
    return result;
  }

  @SuppressWarnings("SleepWhileInLoop")
  public static void pollProject(JScrambler client, String projectId) throws JSONException, Exception {
    if (!JScramblerFacade.silent) {
      System.out.println("Polling server...");
    }
    while (true) {
      String response = (String) client.get("/code/" + projectId + ".json");
      JSONObject result = new JSONObject(response);
      if (result.has("error_id") && result.has("error_message")
          && result.get("error_id").toString().compareTo("null") != 0
          && result.get("error_message").toString().compareTo("null") != 0) {
        if (result.get("error_id").toString().compareTo("0") == 0) {
          break;
        }
        throw new Exception("Error found: " + result.get("error_id").toString()
          + " " + result.get("error_message").toString());
      } else if (!result.has("id") || result.has("error")) {
        throw new Exception("Something went wrong: " + response);
      }
      try {
        Thread.sleep(JScramblerFacade.DELAY * 1000);
      } catch (InterruptedException ex) {
      }
    }
  }

  public static JSONObject uploadCode(JScrambler client, Map<String, Object> params) throws JSONException, IOException {
    if (!JScramblerFacade.silent) {
      System.out.println("Uploading project...");
    }
    // Zip all files before uploading
    JScramblerFacade.zipProject((List<String>) params.get("files"));
    params.put("files", new String[]{JScramblerFacade.ZIP_TMP_FILE});
    // Upload
    String response = client.post("/code.json", params);
    JSONObject object = new JSONObject(response);
    if (object.has("error")) {
      throw new IOException((String) object.get("message"));
    }
    if (!object.has("id")) {
      if (!JScramblerFacade.silent) {
        System.out.println("Response: " + response);
      }
      throw new IOException("No id was found in the post response.");
    }
    if (!JScramblerFacade.silent) {
      System.out.println("Project uploaded");
    }
    return object;
  }

  public static JSONObject deleteCode(JScrambler client, String projectId) throws JSONException, IOException {
    if (!JScramblerFacade.silent) {
      System.out.println("Deleting project...");
    }
    String response = (String) client.delete("/code/" + projectId + ".zip");
    JSONObject result = new JSONObject(response);
    if (result.has("error")) {
      throw new IOException((String) result.get("message"));
    }
    if (!JScramblerFacade.silent) {
      System.out.println("Project deleted");
    }
    return result;
  }

  public static void process(JSONObject config) throws Exception {
    // Check if keys were provided by the config
    if (!config.has("keys")) {
      throw new Exception("Keys must be provided in the configuration.");
    }
    JSONObject keys = config.getJSONObject("keys");
    if (!keys.has("accessKey") || !keys.has("secretKey")) {
      throw new Exception("Access key and secret key must be provided in the configuration.");
    }
    String accessKey = keys.getString("accessKey");
    String secretKey = keys.getString("secretKey");
    // Check if host, port and api version were provided
    String host = null;
    if (config.has("host")) {
      host = config.getString("host");
    }
    Integer port = null;
    if (config.has("port")) {
      port = config.getInt("port");
    }
    String apiVersion = null;
    if (config.has("apiVersion")) {
      apiVersion = config.getString("apiVersion");
    }
    // Instance a JScrambler client
    JScrambler client = new JScrambler(accessKey, secretKey, host, port, apiVersion);
    // Check for source files and add them to parameters
    if (!config.has("filesSrc")) {
      throw new Exception("Source files must be provided.");
    }
    JSONArray files = config.getJSONArray("filesSrc");
    // Check if output was provided
    if (!config.has("filesDest")) {
      throw new Exception("Output directory must be provided.");
    }
    String dest = config.getString("filesDest");
    final List<String> filesSrc = new ArrayList<>();
    for (int i = 0, l = files.length(); i < l; ++i) {
      String origFile = (String) files.get(i);
      if (origFile.startsWith("./")) {
        origFile = origFile.substring(2);
      }
      String globPattern = origFile;
      if (globPattern.startsWith("/")) {
        globPattern = "glob:**" + globPattern;
      } else {
        globPattern = "glob:**/" + globPattern;
      }
      final PathMatcher matcher = FileSystems.getDefault().getPathMatcher(globPattern);

      final Path workingPath = Paths.get(System.getProperty("user.dir"));
      final File crawlFile = new File(origFile);
      Path crawlPath = null;
      if (crawlFile.isFile()) {
        Path pathToAdd = Paths.get(origFile);
        if (pathToAdd.startsWith("../")) {
          pathToAdd = pathToAdd.toAbsolutePath().normalize();
        }
        filesSrc.add(pathToAdd.toString());
        continue;
      } else if (crawlFile.isDirectory()) {
        crawlPath = crawlFile.toPath();
      } else {
        String tmpFile = origFile;
        int starIndex;
        while (true) {
          starIndex = tmpFile.indexOf("*");
          if (starIndex > 0 && !"\\".equals(tmpFile.charAt(starIndex - 1))) {
            tmpFile = tmpFile.substring(starIndex);
          } else {
            break;
          }
        }
        int offset = origFile.length() - tmpFile.length();
        if (offset != 0) {
          crawlPath = new File(origFile.substring(0, offset)).toPath();
        } else {
          crawlPath = crawlFile.toPath();
        }
      }
      Files.walkFileTree(crawlPath, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (matcher.matches(file)) {
              Path sanitizedPath = file;
              if (sanitizedPath.startsWith("../")) {
                sanitizedPath = sanitizedPath.toAbsolutePath().normalize();
              }
              String filePath = sanitizedPath.toString();
              if (!filesSrc.contains(filePath)) {
                filesSrc.add(filePath);
              }
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            return FileVisitResult.CONTINUE;
          }
        });
    }
    // Prepare object to post
    // Check if params were provided
    Map<String, Object> params = new HashMap<>();
    if (config.has("params")) {
      JSONObject paramsJSON = config.getJSONObject("params");
      Iterator it = paramsJSON.keys();
      while (it.hasNext()) {
        String paramKey = (String) it.next();
        params.put(paramKey, paramsJSON.getString(paramKey));
      }
    }
    params.put("files", filesSrc);
    // Send the project to the JScrambler API
    String projectId = (String) JScramblerFacade.uploadCode(client, params).getString("id");
    // Clean the temporary zip file
    JScramblerFacade.cleanZipFile();
    // Download the project and unzip it
    InputStream zipContent = JScramblerFacade.downloadCode(client, projectId);
    if (!JScramblerFacade.silent) {
      System.out.println("Writing...");
    }
    JScramblerFacade.unzipProject(zipContent, dest);
    if (!JScramblerFacade.silent) {
      System.out.println("Written");
    }
  }

  public static void process(String configPath) throws Exception {
    JSONObject config = JScramblerFacade.parseConfig(configPath);
    JScramblerFacade.process(config);
  }

  protected static JSONObject parseConfig(String configPath) throws JSONException, Exception {
    String configString = null;
    try {
      BufferedReader in;
      StringBuilder sb;
      String str;

      in = new BufferedReader(new FileReader(configPath));
      sb = new StringBuilder();

      while ((str = in.readLine()) != null) {
        sb.append(str);
      }

      in.close();
      configString = sb.toString();
    } catch (java.io.IOException e) {}
    if (configString == null) {
      throw new Exception("Failed to read the configuration file " + configPath);
    }
    JSONObject config = new JSONObject(configString);
    return config;
  }

  protected static void cleanZipFile() {
    File file = new File(JScramblerFacade.ZIP_TMP_FILE);
    file.delete();
  }

  protected static void zipProject(List<String> files) throws FileNotFoundException, IOException {
    boolean hasFiles = false;
    if (files.size() == 1 && files.get(0).endsWith(".zip")) {
      try (FileOutputStream fos = new FileOutputStream(JScramblerFacade.ZIP_TMP_FILE);
           FileInputStream fis = new FileInputStream(files.get(0))) {
        hasFiles = true;
        fos.getChannel().transferFrom(fis.getChannel(), 0, fis.getChannel().size());
      }
    } else {
      try (FileOutputStream fos = new FileOutputStream(JScramblerFacade.ZIP_TMP_FILE);
           ZipOutputStream zos = new ZipOutputStream(fos)) {
        for (String filePath : files) {
          File file = new File(filePath);
          if (file.isDirectory()) {
            ZipEntry zipEntry = new ZipEntry(filePath);
            zos.putNextEntry(zipEntry);
            zos.closeEntry();
          } else {
            try (FileInputStream fis = new FileInputStream(file)) {
              ZipEntry zipEntry = new ZipEntry(filePath);
              zos.putNextEntry(zipEntry);

              byte[] bytes = new byte[1024];
              int length;
              while ((length = fis.read(bytes)) >= 0) {
                zos.write(bytes, 0, length);
              }
              zos.closeEntry();
              hasFiles = true;
            }
          }
        }
      } catch (FileNotFoundException ex) {
        if (ex.getMessage().contains("Is a directory")) {
          throw new FileNotFoundException("No source files found. If you intend to send a whole directory sufix your path with \"**\" (e.g. ./my-directory/**)");
        } else {
          throw ex;
        }
      }
    }
    if (!hasFiles) {
      throw new FileNotFoundException("No source files found. If you intend to send a whole directory sufix your path with \"**\" (e.g. ./my-directory/**)");
    }
  }

  protected static void unzipProject(InputStream zipContent, String dest) throws FileNotFoundException, IOException {
    byte[] buffer = new byte[1024];
    File folder = new File(dest);
    if (!folder.exists()) {
      folder.mkdir();
    }
    try (ZipInputStream zis = new ZipInputStream(zipContent)) {
      ZipEntry ze = zis.getNextEntry();

      while (ze != null) {

        String fileName = ze.getName();
        File newFile = new File(dest + File.separator + fileName);
        new File(newFile.getParent()).mkdirs();
        try (FileOutputStream fos = new FileOutputStream(newFile)) {
          int len;
          while ((len = zis.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
          }
        }
        ze = zis.getNextEntry();
      }
      zis.closeEntry();
    }
  }
}
