package com.dailyapps.pocketpet;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** Minimal blocking client for the Anthropic Messages API. Call from a background thread. */
final class ApiClient {
    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";

    private ApiClient() {}

    /** history entries are {role, content}. Returns the assistant's text. */
    static String chat(String apiKey, String model, String system, List<String[]> history)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", 1024);
        if (system != null && system.length() > 0) body.put("system", system);
        JSONArray msgs = new JSONArray();
        for (String[] m : history) {
            JSONObject o = new JSONObject();
            o.put("role", m[0]);
            o.put("content", m[1]);
            msgs.put(o);
        }
        body.put("messages", msgs);

        HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(90000);
            c.setDoOutput(true);
            c.setRequestProperty("content-type", "application/json");
            c.setRequestProperty("x-api-key", apiKey);
            c.setRequestProperty("anthropic-version", "2023-06-01");

            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            int code = c.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            String resp = readAll(is);
            if (code < 200 || code >= 300) {
                throw new IOException(describeError(code, resp));
            }
            JSONObject j = new JSONObject(resp);
            JSONArray content = j.getJSONArray("content");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.getJSONObject(i);
                if ("text".equals(block.optString("type"))) {
                    sb.append(block.optString("text"));
                }
            }
            String out = sb.toString().trim();
            if (out.length() == 0) throw new IOException("The model returned an empty reply.");
            return out;
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        br.close();
        return sb.toString();
    }

    private static String describeError(int code, String resp) {
        String msg = "";
        try {
            JSONObject j = new JSONObject(resp);
            JSONObject err = j.optJSONObject("error");
            if (err != null) msg = err.optString("message", "");
        } catch (Exception ignored) {
        }
        String hint;
        if (code == 401) hint = "Your API key was rejected. Check it in Settings.";
        else if (code == 403) hint = "This key isn't allowed to use that model.";
        else if (code == 404) hint = "Model not found. Check the model name in Settings.";
        else if (code == 429) hint = "Rate limited or out of credit. Try again shortly.";
        else if (code >= 500) hint = "The API is having trouble. Try again.";
        else hint = "Request failed.";
        return hint + " (HTTP " + code + ")" + (msg.length() > 0 ? "\n" + msg : "");
    }
}
