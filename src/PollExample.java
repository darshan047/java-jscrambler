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
import java.util.*;
import org.json.*;

/**
 * Simple program that uses JScrambler client in java to upload a JavaScript 
 * project for obfuscation and poll the server to download the obfuscated
 * project as soon as is finished.
 */
public class PollExample {

    final static int DEFAULT_DELAY_IN_SECONDS = 5;
    final static String DEFAULT_CONFIG_FILE_PATH = "config.json";

    public static void main(String[] args) {

        int delaySeconds = DEFAULT_DELAY_IN_SECONDS;
        String configFilePath = DEFAULT_CONFIG_FILE_PATH,
                destinationPath,
                configFileContents,
                accessKey,
                secretKey,
                server = null;
        JSONArray jsonFilesArray;
        String[] filesList;
        Integer port = null;
        JSONObject jsonConfigObj,
                jsonParamsObj,
                jsonConnObj;
        Map postParams;
        JScrambler jscrambler;

        // Validate java arguments
        if (args.length > 0 && args.length < 4) {
            if (args[0] == null) {
                System.err.println("Setting destination path is mandatory.\n'java -jar example.jar destination_path [delay_in_seconds [config_file_path]]'");
                return;
            }
            destinationPath = args[0];
            if (args.length > 1 && args[1] != null) {
                try {
                    delaySeconds = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignore) {
                    System.err.println("Delay in seconds is not a valid integer.\n'java -jar example.jar destination_path [delay_in_seconds [config_file_path]]'");
                    return;
                }
            }
            if (args.length == 3 && args[2] != null) {
                configFilePath = args[2];
            }

        } else {
            System.err.println("Wrong number of arguments.\n'java -jar example.jar destination_path [delay_in_seconds [config_file_path]]'");
            return;
        }

        // Read and parse JSON configuration file
        configFileContents = readFile(configFilePath);

        if (configFileContents == null) {
            System.err.println("Failed to read configuration file '" + configFilePath + "' on current directory.");
            return;
        }

        try {
            jsonConfigObj = new JSONObject(configFileContents);
        } catch (org.json.JSONException ignore) {
            System.err.println("Failed to decode json configuration file.");
            return;
        }

        // Get parameters from files json array (or string)
        try {
            Object jsonFilesObj = jsonConfigObj.get("files");

            if (jsonFilesObj instanceof String) {
                filesList = new String[]{jsonConfigObj.getString("files")};
            } // get file names from json array 
            else if (jsonFilesObj instanceof JSONArray) {
                jsonFilesArray = jsonConfigObj.getJSONArray("files");
                filesList = new String[jsonFilesArray.length()];
                for (int i = 0; i < jsonFilesArray.length(); i++) {
                    filesList[i] = jsonFilesArray.getString(i);
                }
            } else {
                throw new Exception();
            }

        } catch (Exception ignore) {
            System.err.println("A valid 'files' parameter was not found on configuration file.");
            return;
        }

        // POST parameters
        postParams = new HashMap();
        postParams.put("files", filesList);

        try {
            jsonParamsObj = jsonConfigObj.getJSONObject("parameters");
        } catch (JSONException ignore) {
            System.err.println("'parameters' parameter was not found on configuration file.");
            return;
        }

        // Get parameters from params json object
        Iterator it = jsonParamsObj.keys();
        String paramKey, paramValue;
        while (it.hasNext()) {
            paramKey = (String) it.next();
            paramValue = null;
            try {
                paramValue = (String) jsonParamsObj.get(paramKey);
            } catch (org.json.JSONException ignore) {
                System.err.println("Failed to find a value for key \"" + paramKey);
            }
            postParams.put(paramKey, paramValue);
        }

        // Get json connection parameters
        try {
            jsonConnObj = jsonConfigObj.getJSONObject("connection");
        } catch (JSONException ignore) {
            System.err.println("'connection' parameter was not found on configuration file.");
            return;
        }

        try {
            accessKey = jsonConnObj.getString("access_key");
            secretKey = jsonConnObj.getString("secret_key");
        } catch (JSONException ignore) {
            System.err.println("'access_key' or 'secret_key' parameters not found on configuration file.");
            return;
        }

        // Use default if those are not defined
        try {
            server = jsonConnObj.getString("server");
            port = jsonConnObj.getInt("port");
        } catch (JSONException ignore) {
        }

        // Init jscrambler client
        jscrambler = new JScrambler(accessKey, secretKey, server, port);

        String postResponse,
                getResponse = null;
        InputStream getResponseZip;
        JSONObject postResponseObj,
                getResponseObj;

        System.out.println("Sending files to server.");

        postResponse = jscrambler.post("/code.json", postParams);

        if (postResponse == null) {
            System.err.println("Failed to send project to server.");
            return;
        }

        try {
            postResponseObj = new JSONObject(postResponse);
        } catch (org.json.JSONException ignore) {
            System.err.println("Failed to decode json POST response.");
            return;
        }

        if (!postResponseObj.has("id")
                || postResponseObj.has("error")) {
            System.err.println("Something went wrong.\n" + postResponse);
            return;
        }

        //
        // Poll server
        //
        System.out.println("Polling server...");

        while (true) {

            try {
                getResponse = (String) jscrambler.get("/code/" + postResponseObj.getString("id") + ".json");
            } catch (JSONException ignore) {
            }

            try {
                getResponseObj = new JSONObject(getResponse);
            } catch (org.json.JSONException ignore) {
                System.err.println("Failed to decode json GET response.");
                return;
            }

            // Check project state before download
            try {
                if (getResponseObj.has("error_id")
                        && getResponseObj.has("error_message")
                        && getResponseObj.get("error_id").toString().compareTo("null") != 0
                        && getResponseObj.get("error_message").toString().compareTo("null") != 0) {



                    if (getResponseObj.get("error_id").toString().compareTo("0") == 0) {

                        System.out.println("Ready to download.");
                        break;
                    }

                    System.err.println("Error found.\nid: " + getResponseObj.getString("error_id")
                            + "\nmessage: " + getResponseObj.getString("error_message"));
                    return;

                } else if (!getResponseObj.has("id")
                        || getResponseObj.has("error")) {
                    System.err.println("Something went wrong.\n" + getResponse);
                    return;
                }

            } catch (org.json.JSONException ignore) {
                System.err.println("Failed to check project status.");
                return;
            }

            try {
                System.out.println("Not ready. Next poll in " + delaySeconds + " seconds.");
                waitSeconds(delaySeconds);
            } catch (InterruptedException ignore) {
            }

        }

        String resultPath;

        try {
            resultPath = destinationPath + File.separator + getResponseObj.getString("id") + "." + getResponseObj.getString("extension");
            getResponseZip = (InputStream) jscrambler.get("/code/" + getResponseObj.getString("id") + ".zip");

            System.out.print("Writting file to '" + resultPath + "'... ");
            writeFile(resultPath, getResponseZip);

        } catch (org.json.JSONException ignore) {
        }

        System.out.println("Done.");

    }

    /**
     * Writes contents to file
     * 
     * @param filepath
     * @param input
     * @return 
     */
    static private boolean writeFile(String filepath, InputStream input) {

        OutputStream output = null;
        byte[] buffer = new byte[1024];

        try {
            try {
                output = new FileOutputStream(filepath);
                for (int length; (length = input.read(buffer)) > 0;) {
                    output.write(buffer, 0, length);
                }
            } catch (java.io.FileNotFoundException ignore) {
                System.out.println("File '" + filepath + "' not found.");
                return false;
            } catch (java.io.IOException ignore) {
                return false;
            }

        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (java.io.IOException ignore) {
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (java.io.IOException ignore) {
                }
            }
        }
        return true;
    }

    /**
     * Reads the contents of a file
     *
     * @param filepath
     * @return contents of a file or null if fails
     */
    static private String readFile(String filepath) {
        try {
            BufferedReader in;
            StringBuilder sb;
            String str;

            in = new BufferedReader(new FileReader(filepath));
            sb = new StringBuilder();

            while ((str = in.readLine()) != null) {
                sb.append(str);
            }

            in.close();
            return sb.toString();

        } catch (java.io.IOException e) {
            return null;
        }
    }

    static private void waitSeconds(int delaySeconds) throws InterruptedException {
        Thread.sleep(delaySeconds * 1000);
    }
}
