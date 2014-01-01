package com.goebl.david;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight Java HTTP-Client for calling JSON REST-Services (especially for Android).
 *
 * @author hgoebl
 */
public class Webb {
    public static final String DEFAULT_USER_AGENT = Const.DEFAULT_USER_AGENT;
    public static final String APP_FORM = Const.APP_FORM;
    public static final String APP_JSON = Const.APP_JSON;
    public static final String APP_BINARY = Const.APP_BINARY;
    public static final String TEXT_PLAIN = Const.TEXT_PLAIN;
    public static final String HDR_CONTENT_TYPE = Const.HDR_CONTENT_TYPE;
    public static final String HDR_ACCEPT = Const.HDR_ACCEPT;
    public static final String HDR_USER_AGENT = Const.HDR_USER_AGENT;
    public static final String HDR_AUTHORIZATION = "Authorization";

    static final Map<String, Object> globalHeaders = new LinkedHashMap<String, Object>();
    static String globalBaseUri;

    static int connectTimeout = 10000;
    static int readTimeout = 60000;
    static int jsonIndentFactor = -1;

    boolean followRedirects = false;
    String baseUri;
    Map<String, Object> defaultHeaders;
    SSLSocketFactory sslSocketFactory;
    HostnameVerifier hostnameVerifier;

    private Webb() {}

    /**
     * Create an instance which can be reused for multiple requests in the same Thread.
     * @return the created instance.
     */
    public static Webb create() {
        return new Webb();
    }

    /**
     * Set the value for a named header which is valid for all requests in the running JVM.
     * <br/>
     * The value can be overwritten by calling {@link Webb#setDefaultHeader(String, Object)} and/or
     * {@link com.goebl.david.Request#header(String, Object)}.
     * <br/>
     * For the supported types for values see {@link Request#header(String, Object)}.
     *
     * @param name name of the header (regarding HTTP it is not case-sensitive, but here case is important).
     * @param value value of the header. If <code>null</code> the header value is cleared (effectively not set).
     *
     * @see #setDefaultHeader(String, Object)
     * @see com.goebl.david.Request#header(String, Object)
     */
    public static void setGlobalHeader(String name, Object value) {
        if (value != null) {
            globalHeaders.put(name, value);
        } else {
            globalHeaders.remove(name);
        }
    }

    /**
     * Set the base URI for all requests starting in this JVM from now.
     * <br/>
     * For all requests this value is taken as a kind of prefix for the effective URI, so you can address
     * the URIs relatively. The value is only taken when {@link Webb#setBaseUri(String)} is not called or
     * called with <code>null</code>.
     *
     * @param globalBaseUri the prefix for all URIs of new Requests.
     * @see #setBaseUri(String)
     */
    public static void setGlobalBaseUri(String globalBaseUri) {
        Webb.globalBaseUri = globalBaseUri;
    }

    /**
     * The number of characters to indent child properties, <code>-1</code> for "productive" code.
     * <br>
     * Default is production ready JSON (-1) means no indentation (single-line serialization).
     * @param indentFactor the number of spaces to indent
     */
    public static void setJsonIndentFactor(int indentFactor) {
        Webb.jsonIndentFactor = indentFactor;
    }

    /**
     * Set a custom {@link javax.net.ssl.SSLSocketFactory}, most likely to relax Certification checking.
     * @param sslSocketFactory the factory to use (see test cases for an example).
     */
    public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
    }

    /**
     * Set a custom {@link javax.net.ssl.HostnameVerifier}, most likely to relax host-name checking.
     * @param hostnameVerifier the verifier (see test cases for an example).
     */
    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }

    /**
     * Set the base URI for all requests created from this instance.
     * <br/>
     * For all requests this value is taken as a kind of prefix for the effective URI, so you can address
     * the URIs relatively. The value takes precedence over the value set in {@link #setGlobalBaseUri(String)}.
     *
     * @param baseUri the prefix for all URIs of new Requests.
     * @see #setGlobalBaseUri(String)
     */
    public void setBaseUri(String baseUri) {
        this.baseUri = baseUri;
    }

    /**
     * Set the value for a named header which is valid for all requests created by this instance.
     * <br/>
     * The value takes precedence over {@link Webb#setGlobalHeader(String, Object)} but can be overwritten by
     * {@link com.goebl.david.Request#header(String, Object)}.
     * <br/>
     * For the supported types for values see {@link Request#header(String, Object)}.
     *
     * @param name name of the header (regarding HTTP it is not case-sensitive, but here case is important).
     * @param value value of the header. If <code>null</code> the header value is cleared (effectively not set).
     *              When setting the value to null, a value from global headers can shine through.
     *
     * @see #setGlobalHeader(String, Object)
     * @see com.goebl.david.Request#header(String, Object)
     */
    public void setDefaultHeader(String name, Object value) {
        if (defaultHeaders == null) {
            defaultHeaders = new HashMap<String, Object>();
        }
        if (value == null) {
            defaultHeaders.remove(name);
        } else {
            defaultHeaders.put(name, value);
        }
    }

    /**
     * Creates a <b>GET HTTP</b> request with the specified absolute or relative URI.
     * @param pathOrUri the URI (will be concatenated with global URI or default URI without further checking)
     * @return the created Request object (in fact it's more a builder than a real request object)
     */
    public Request get(String pathOrUri) {
        return new Request(this, Request.Method.GET, buildPath(pathOrUri));
    }

    /**
     * Creates a <b>POST</b> HTTP request with the specified absolute or relative URI.
     * @param pathOrUri the URI (will be concatenated with global URI or default URI without further checking)
     * @return the created Request object (in fact it's more a builder than a real request object)
     */
    public Request post(String pathOrUri) {
        return new Request(this, Request.Method.POST, buildPath(pathOrUri));
    }

    /**
     * Creates a <b>PUT</b> HTTP request with the specified absolute or relative URI.
     * @param pathOrUri the URI (will be concatenated with global URI or default URI without further checking)
     * @return the created Request object (in fact it's more a builder than a real request object)
     */
    public Request put(String pathOrUri) {
        return new Request(this, Request.Method.PUT, buildPath(pathOrUri));
    }

    /**
     * Creates a <b>DELETE</b> HTTP request with the specified absolute or relative URI.
     * @param pathOrUri the URI (will be concatenated with global URI or default URI without further checking)
     * @return the created Request object (in fact it's more a builder than a real request object)
     */
    public Request delete(String pathOrUri) {
        return new Request(this, Request.Method.DELETE, buildPath(pathOrUri));
    }

    private String buildPath(String pathOrUri) {
        String myBaseUri = baseUri != null ? baseUri : globalBaseUri;
        return myBaseUri == null ? pathOrUri : myBaseUri + pathOrUri;
    }

    <T> Response<T> execute(Request request, Class<T> clazz) {
        Response<T> response = new Response<T>(request);

        InputStream is = null;
        HttpURLConnection connection = null;
        HttpURLConnection.setFollowRedirects(followRedirects);

        try {
            String uri = request.uri;
            if (request.method == Request.Method.GET &&
                    !uri.contains("?") &&
                    request.params != null &&
                    !request.params.isEmpty()) {
                uri += "?" + WebbUtils.queryString(request.params);
            }
            URL apiUrl = new URL(uri);
            connection = (HttpURLConnection) apiUrl.openConnection();

            prepareSslConnection(connection);
            connection.setRequestMethod(request.method.name());
            connection.setInstanceFollowRedirects(followRedirects);
            connection.setUseCaches(request.useCaches);
            connection.setConnectTimeout(request.connectTimeout != null ? request.connectTimeout : connectTimeout);
            connection.setReadTimeout(request.readTimeout != null ? request.readTimeout : readTimeout);
            if (request.ifModifiedSince != null) {
                connection.setIfModifiedSince(request.ifModifiedSince);
            }

            WebbUtils.addRequestProperties(connection, mergeHeaders(request.headers));
            if (clazz == JSONObject.class || clazz == JSONArray.class) {
                WebbUtils.ensureRequestProperty(connection, HDR_ACCEPT, APP_JSON);
            }

            byte[] requestBody = null;
            if (request.method != Request.Method.GET && request.method != Request.Method.DELETE) {
                requestBody = WebbUtils.getPayloadAsBytesAndSetContentType(connection, request, jsonIndentFactor);
                if (requestBody != null) {
                    connection.setDoOutput(true);
                }
            }

            connection.connect();

            // write the body (of course headers are written first by HUC)
            if (requestBody != null) {
                if (request.streamPayload) {
                    streamBody(connection, request.payload);
                } else {
                    writeBody(connection, requestBody);
                }
            }

            // get the response body (if any)
            is = connection.getInputStream();
            byte[] responseBody = WebbUtils.readBytes(is);

            response.connection = connection;
            response.statusCode = connection.getResponseCode();
            response.responseMessage = connection.getResponseMessage();

            if (request.ensureSuccess) {
                response.ensureSuccess();
            }

            WebbUtils.parseResponseBody(clazz, response, responseBody);

            return response;

        } catch (WebbException e) {

            throw e;

        } catch (Exception e) {

            throw new WebbException(e);

        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignored) {}
            }
            if (connection != null) {
                try { connection.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private void writeBody(HttpURLConnection connection, byte[] body) throws IOException {
        OutputStream os = null;
        try {
            os = connection.getOutputStream();
            os.write(body);
            os.flush();
        } finally {
            if (os != null) {
                try { os.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void streamBody(HttpURLConnection connection, Object body) throws IOException {
        InputStream is;
        boolean closeStream;

        if (body instanceof File) {
            is = new FileInputStream((File) body);
            closeStream = true;
        } else {
            is = (InputStream) body;
            closeStream = false;
        }

        OutputStream os = null;
        try {
            os = connection.getOutputStream();
            WebbUtils.copyStream(is, os);
            os.flush();
        } finally {
            if (os != null) {
                try { os.close(); } catch (Exception ignored) {}
            }
            if (is != null && closeStream) {
                try { is.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void prepareSslConnection(HttpURLConnection connection) {
        if ((hostnameVerifier != null || sslSocketFactory != null) && connection instanceof HttpsURLConnection) {
            HttpsURLConnection sslConnection = (HttpsURLConnection) connection;
            if (hostnameVerifier != null) {
                sslConnection.setHostnameVerifier(hostnameVerifier);
            }
            if (sslSocketFactory != null) {
                sslConnection.setSSLSocketFactory(sslSocketFactory);
            }
        }
    }

    Map<String, Object> mergeHeaders(Map<String, Object> requestHeaders) {
        Map<String, Object> headers = null;
        if (!globalHeaders.isEmpty()) {
            headers = new LinkedHashMap<String, Object>();
            headers.putAll(globalHeaders);
        }
        if (defaultHeaders != null) {
            if (headers == null) {
                headers = new LinkedHashMap<String, Object>();
            }
            headers.putAll(defaultHeaders);
        }
        if (requestHeaders != null) {
            if (headers == null) {
                headers = requestHeaders;
            } else {
                headers.putAll(requestHeaders);
            }
        }
        return headers;
    }

}