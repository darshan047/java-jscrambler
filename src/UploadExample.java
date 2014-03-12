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

import com.auditmark.jscrambler.client.JScrambler;

/**
 * Simple program that uses JScrambler client in java to upload a JavaScript 
 * project for obfuscation.
 */
public class UploadExample {

    public static void main(String[] args) {

        String accessKey = "YOUR_ACCESS_KEY";
        String secretKey = "YOUR_SECRET_KEY";
        String apiUrl = "api.jscrambler.com";
        int port = 443;
        
        JScrambler jscrambler = new JScrambler(accessKey, secretKey, apiUrl, port);

        java.util.Map params = new java.util.HashMap();
        params.put("files", new String[]{"index.html", "script.js"});
        params.put("domain_lock","jscrambler.com"); 
        params.put("expiration_date","2099/12/31");

        String response = jscrambler.post("/code.json", params);

        System.out.println(response);
    }
}