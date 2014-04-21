/*
 * Copyright 2013, 2013 AuditMark
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

import java.io.*;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

public class JScrambler {

    private String accessKey,
                   secretKey,
                   apiHost;
    private int apiPort,
                apiVersion;

    public JScrambler(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        apiHost = "api.jscrambler.com";
        apiPort = 80;
        this.apiVersion = 3;
    }

    public JScrambler(String accessKey, String secretKey, String apiHost) {
        this(accessKey, secretKey);
        this.apiHost = apiHost;
    }

    public JScrambler(String accessKey, String secretKey, String apiHost, int apiPort) {
        this(accessKey, secretKey, apiHost);
        this.apiPort = apiPort;
    }

    /**
     * If the content-type of the response equals application/zip returns an
     * inputstream. Otherwise returns a string.
     * 
     * @param resourcePath
     * @param params
     * @return 
     */
    public Object get(String resourcePath, Map params) {
        try {
            return httpRequest("GET", resourcePath, params);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    }
    
    public Object get(String resourcePath) {
        return get(resourcePath, null);
    }
    

    public String post(String resourcePath, Map params) {
        try {
            return (String) httpRequest("POST", resourcePath, params);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    public String delete(String resourcePath, Map params) {
        try {
            return (String) httpRequest("DELETE", resourcePath, params);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    }
    
    public String delete(String resourcePath) {
        return delete(resourcePath, null);
    }

    private String apiURL() {
        return "http" + (apiPort == 443 ? "s" : "") + "://" + apiHost + (apiPort != 80 ? (":" + apiPort) : "") + "/v" + apiVersion;
    }

    private Object httpRequest(String requestMethod, String resourcePath, Map params) throws IOException, Exception {

        String signedData = null,
                urlQueryPart = null;

        if (requestMethod.toUpperCase().equals("POST") && params.isEmpty()) {
            throw new IllegalArgumentException("Parameters missing for POST request.");
        }
        
        String[] files = null; 

        if(params == null) {
            params = new TreeMap();
        } else {
            if (params.get("files") instanceof String) {
                files = new String[]{(String) params.get("files")};
                params.put("files", files);
            } else if(params.get("files") instanceof String[]) {
                files = (String[]) params.get("files");
            }
            params = new TreeMap(params);
        }
        
        if (requestMethod.toUpperCase().equals("POST")) {
            signedData = signedQuery(requestMethod, resourcePath, params, null);
        } else {
            urlQueryPart = "?" + signedQuery(requestMethod, resourcePath, params, null);
        }

        // http client
        HttpClient httpclient = new DefaultHttpClient();
        HttpResponse response = null;

        try {
            if (signedData != null && requestMethod.toUpperCase().equals("POST")) {
                HttpPost httppost = new HttpPost(apiURL() + resourcePath + (urlQueryPart != null ? urlQueryPart : ""));
                MultipartEntity reqEntity = new MultipartEntity();
                
                if (files != null) {
                    int n = 0;
                    for (String filename : files) {
                        FileBody fb = new FileBody(new File(filename));
                        reqEntity.addPart("file_" + n++, fb);
                    }
                }
                
                for (String param : (Set<String>) params.keySet()) {
                    if (param.equals("files") || param.startsWith("file_")) {
                        continue;
                    }
                    if (params.get(param) instanceof String) {
                        reqEntity.addPart(param, new StringBody((String) params.get(param)));
                    }
                }
                
                httppost.setEntity(reqEntity);
                response = httpclient.execute(httppost);

            } else if (requestMethod.toUpperCase().equals("GET")) {
                HttpGet request = new HttpGet(apiURL() + resourcePath + (urlQueryPart != null ? urlQueryPart : ""));
                response = httpclient.execute(request);
            } else if (requestMethod.toUpperCase().equals("DELETE")) {
                HttpDelete request = new HttpDelete(apiURL() + resourcePath + (urlQueryPart != null ? urlQueryPart : ""));
                response = httpclient.execute(request);
            } else {
                throw new Exception("Invalid request method.");
            }

            HttpEntity httpEntity = response.getEntity();

            // if GET Zip archive
            if (requestMethod.toUpperCase().equals("GET") &&
                    resourcePath.toLowerCase().endsWith(".zip") &&
                    response.getStatusLine().getStatusCode() == 200) {
                // Return inputstream
                return httpEntity.getContent();
            }
            // Otherwise return string
            return 
		EntityUtils.toString(httpEntity, "UTF-8");

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw e;
        } 
    }

    private String signedQuery(String requestMethod, String resourcePath, Map params, SimpleDateFormat timestamp) throws FileNotFoundException, InvalidKeyException, NoSuchAlgorithmException, IOException, Exception {
        Map signedParams = signedParams(requestMethod, resourcePath, params, timestamp);
        return urlQueryString(signedParams);
    }

    private Map signedParams(String requestMethod, String resourcePath, Map params, SimpleDateFormat timestamp) throws FileNotFoundException, InvalidKeyException, NoSuchAlgorithmException, IOException, Exception {
        if (requestMethod.toUpperCase().equals("POST") && params.containsKey("files")) {
            Map fileParams = constructFileParams((String[]) params.get("files"));
            params.remove("files");
            params.putAll(fileParams);
        }
        params.put("timestamp", (timestamp != null ? timestamp : (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")).format(new Date()).toString()));
        params.put("access_key", accessKey);
        params.put("signature", generateHMACSignature(requestMethod, resourcePath, params));
        return params;
    }
    
    private static Map constructFileParams(String[] files) throws FileNotFoundException, IOException, Exception {
        int counter = 0;
        Map map = new HashMap();
        for (String filePath : files) {
            File file = new File(filePath);
            StringBuilder contents = new StringBuilder();
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(file));
                String text;
                while ((text = reader.readLine()) != null) {
                    contents.append(text).append(System.getProperty(
                            "line.separator"));
                }
                map.put("file_" + (counter++), md5sum(filePath));
            } catch (FileNotFoundException e) {
                System.err.println("File '" + filePath + "' not found.");
                throw e;
            } catch (IOException e) {
                throw e;
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    throw e;
                }
            }
        }
        return map;
    }

    private static String md5sum(String filePath) throws Exception {
        try {
            byte[] b = createChecksum(filePath);
            String result = "";
            for (int i = 0; i < b.length; i++) {
                result +=
                        Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
            }
            return result;
        } catch (Exception e) {
            throw e;
        }
    }

    private static byte[] createChecksum(String filename) throws Exception {
        InputStream fis = new FileInputStream(filename);
        byte[] buffer = new byte[1024];
        MessageDigest complete = MessageDigest.getInstance("MD5");
        int numRead;
        do {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                complete.update(buffer, 0, numRead);
            }
        } while (numRead != -1);
        fis.close();
        return complete.digest();
    }

    private String generateHMACSignature(String requestMethod, String resourcePath, Map<String, String> params) throws InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException {
        String data = hmacSignatureData(requestMethod, resourcePath, apiHost, params);
        try {
            SecretKeySpec signingKey = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            byte[] digest = mac.doFinal(data.getBytes());
            return new sun.misc.BASE64Encoder().encode(digest);

        } catch (InvalidKeyException e) {
            System.err.println("Invalid key: " + e.getMessage());
            throw e;
        } catch (NoSuchAlgorithmException e) {
            System.err.println("No such algorithm: " + e.getMessage());
            throw e;
        }
    }

    private String hmacSignatureData(String requestMethod, String resourcePath, String host, Map<String, String> params) throws UnsupportedEncodingException {
        return requestMethod.toUpperCase() + ";" + host.toLowerCase() + ";" + resourcePath + ";" + urlQueryString(params);
    }

    private static String urlQueryString(Map<String, String> params) throws UnsupportedEncodingException {
        TreeSet<String> keys = new TreeSet<String>(params.keySet());
        String query = "";
        for (String key : keys) {
            Object obj = params.get(key);
            if (obj.getClass().toString().equals(new String().getClass().toString())) {
                String value = params.get(key);
                query += urlEncode(key) + "=" + urlEncode(value) + "&";
            }
        }
        return query.substring(0, query.length() - 1);
    }

    private static String urlEncode(String data) throws UnsupportedEncodingException {
        String encodedStr = null;
        try {
            encodedStr = java.net.URLEncoder.encode(data, "UTF-8");
            encodedStr = encodedStr.replace("%7E", "~");
            encodedStr = encodedStr.replace("+", "%20");
            encodedStr = encodedStr.replace("*", "%2A");
        } catch (UnsupportedEncodingException e) {
            throw e;
        }
        return encodedStr;
    }
    
    public static final int SUCCESS = 0;
}
