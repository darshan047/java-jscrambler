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
import java.io.*;

/**
 * Simple program that uses JScrambler client in java to download obfuscated 
 * JavaScript project.
 */
public class DownloadExample {

    public static void main(String[] args) {
        
        String accessKey = "YOUR_ACCESS_KEY";
        String secretKey = "YOUR_SECRET_KEY";
        String apiUrl = "api.jscrambler.com";
        int port = 443;
        
        JScrambler jscrambler = new JScrambler(accessKey, secretKey, apiUrl, port);
        
        String projectId = "YOUR_PROJECT_ID";
        String apiResourcePath = "/code/"+projectId+".zip";
        String filenamePath = "/your/machine/pathto/"+projectId+".zip";
        

        OutputStream output = null;
        InputStream input = (InputStream) jscrambler.get(apiResourcePath);
        byte[] buffer = new byte[1024];

        try {
            output = new FileOutputStream(filenamePath);
            for (int length; (length = input.read(buffer)) > 0;) {
                // write contents to output
                output.write(buffer, 0, length);
            }
        } catch(FileNotFoundException e) {
            System.err.println(e.getMessage());
        }  catch(IOException e) {
            System.err.println(e.getMessage());
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }
        }
    }
}