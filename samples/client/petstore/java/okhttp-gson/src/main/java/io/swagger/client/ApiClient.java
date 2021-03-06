package io.swagger.client;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.internal.http.HttpMethod;

import java.lang.reflect.Type;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.net.URLEncoder;
import java.net.URLConnection;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;

import okio.BufferedSink;
import okio.Okio;

import io.swagger.client.auth.Authentication;
import io.swagger.client.auth.HttpBasicAuth;
import io.swagger.client.auth.ApiKeyAuth;
import io.swagger.client.auth.OAuth;

public class ApiClient {
  private String basePath = "http://petstore.swagger.io/v2";
  private boolean lenientOnJson = false;
  private boolean debugging = false;
  private Map<String, String> defaultHeaderMap = new HashMap<String, String>();
  private String tempFolderPath = null;

  private Map<String, Authentication> authentications;

  private int statusCode;
  private Map<String, List<String>> responseHeaders;

  private String dateFormat;
  private DateFormat dateFormatter;
  private int dateLength;

  private String datetimeFormat;
  private DateFormat datetimeFormatter;

  private OkHttpClient httpClient;
  private JSON json;

  public ApiClient() {
    httpClient = new OkHttpClient();

    json = new JSON(this);

    // Use ISO 8601 format for date and datetime.
    // See https://en.wikipedia.org/wiki/ISO_8601
    setDateFormat("yyyy-MM-dd");
    setDatetimeFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    // Set default User-Agent.
    setUserAgent("Java-Swagger");

    // Setup authentications (key: authentication name, value: authentication).
    authentications = new HashMap<String, Authentication>();
    authentications.put("api_key", new ApiKeyAuth("header", "api_key"));
    authentications.put("petstore_auth", new OAuth());
    // Prevent the authentications from being modified.
    authentications = Collections.unmodifiableMap(authentications);
  }

  public String getBasePath() {
    return basePath;
  }

  public ApiClient setBasePath(String basePath) {
    this.basePath = basePath;
    return this;
  }

  public OkHttpClient getHttpClient() {
    return httpClient;
  }

  public ApiClient setHttpClient(OkHttpClient httpClient) {
    this.httpClient = httpClient;
    return this;
  }

  public JSON getJSON() {
    return json;
  }

  public ApiClient setJSON(JSON json) {
    this.json = json;
    return this;
  }

  /**
   * Gets the status code of the previous request.
   * NOTE: Status code of last async response is not recorded here, it is
   * passed to the callback methods instead.
   */
  public int getStatusCode() {
    return statusCode;
  }

  /**
   * Gets the response headers of the previous request.
   * NOTE: Headers of last async response is not recorded here, it is passed
   * to callback methods instead.
   */
  public Map<String, List<String>> getResponseHeaders() {
    return responseHeaders;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public ApiClient setDateFormat(String dateFormat) {
    this.dateFormat = dateFormat;

    this.dateFormatter = new SimpleDateFormat(dateFormat);
    // Use UTC as the default time zone.
    this.dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));

    this.dateLength = this.dateFormatter.format(new Date()).length();

    return this;
  }

  public String getDatetimeFormat() {
    return datetimeFormat;
  }

  public ApiClient setDatetimeFormat(String datetimeFormat) {
    this.datetimeFormat = datetimeFormat;

    this.datetimeFormatter = new SimpleDateFormat(datetimeFormat);
    // Note: The datetime formatter uses the system's default time zone.

    return this;
  }

  /**
   * Parse the given date string into Date object.
   * The default <code>dateFormat</code> supports these ISO 8601 date formats:
   *   2015-08-16
   *   2015-8-16
   */
  public Date parseDate(String str) {
    if (str == null)
      return null;
    try {
      return dateFormatter.parse(str);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Parse the given date-time string into Date object.
   * The default <code>datetimeFormat</code> supports these ISO 8601 datetime formats:
   *   2015-08-16T08:20:05Z
   *   2015-8-16T8:20:05Z
   *   2015-08-16T08:20:05+00:00
   *   2015-08-16T08:20:05+0000
   *   2015-08-16T08:20:05.376Z
   *   2015-08-16T08:20:05.376+00:00
   *   2015-08-16T08:20:05.376+00
   * Note: The 3-digit milli-seconds is optional. Time zone is required and can be in one of
   *   these formats:
   *   Z (same with +0000)
   *   +08:00 (same with +0800)
   *   -02 (same with -0200)
   *   -0200
   * @see https://en.wikipedia.org/wiki/ISO_8601
   */
  public Date parseDatetime(String str) {
    if (str == null)
      return null;

    if ("yyyy-MM-dd'T'HH:mm:ss.SSSZ".equals(datetimeFormat)) {
      /*
       * When the default datetime format is used, process the given string
       * to support various formats defined by ISO 8601.
       */
      // normalize time zone
      //   trailing "Z": 2015-08-16T08:20:05Z => 2015-08-16T08:20:05+0000
      str = str.replaceAll("[zZ]\\z", "+0000");
      //   remove colon: 2015-08-16T08:20:05+00:00 => 2015-08-16T08:20:05+0000
      str = str.replaceAll("([+-]\\d{2}):(\\d{2})\\z", "$1$2");
      //   expand time zone: 2015-08-16T08:20:05+00 => 2015-08-16T08:20:05+0000
      str = str.replaceAll("([+-]\\d{2})\\z", "$100");
      // add milliseconds when missing
      //   2015-08-16T08:20:05+0000 => 2015-08-16T08:20:05.000+0000
      str = str.replaceAll("(:\\d{1,2})([+-]\\d{4})\\z", "$1.000$2");
    }

    try {
      return datetimeFormatter.parse(str);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  public Date parseDateOrDatetime(String str) {
    if (str == null)
      return null;
    else if (str.length() <= dateLength)
      return parseDate(str);
    else
      return parseDatetime(str);
  }

  /**
   * Format the given Date object into string.
   */
  public String formatDate(Date date) {
    return dateFormatter.format(date);
  }

  /**
   * Format the given Date object into string.
   */
  public String formatDatetime(Date date) {
    return datetimeFormatter.format(date);
  }

  /**
   * Get authentications (key: authentication name, value: authentication).
   */
  public Map<String, Authentication> getAuthentications() {
    return authentications;
  }

  /**
   * Get authentication for the given name.
   *
   * @param authName The authentication name
   * @return The authentication, null if not found
   */
  public Authentication getAuthentication(String authName) {
    return authentications.get(authName);
  }

  /**
   * Helper method to set username for the first HTTP basic authentication.
   */
  public void setUsername(String username) {
    for (Authentication auth : authentications.values()) {
      if (auth instanceof HttpBasicAuth) {
        ((HttpBasicAuth) auth).setUsername(username);
        return;
      }
    }
    throw new RuntimeException("No HTTP basic authentication configured!");
  }

  /**
   * Helper method to set password for the first HTTP basic authentication.
   */
  public void setPassword(String password) {
    for (Authentication auth : authentications.values()) {
      if (auth instanceof HttpBasicAuth) {
        ((HttpBasicAuth) auth).setPassword(password);
        return;
      }
    }
    throw new RuntimeException("No HTTP basic authentication configured!");
  }

  /**
   * Helper method to set API key value for the first API key authentication.
   */
  public void setApiKey(String apiKey) {
    for (Authentication auth : authentications.values()) {
      if (auth instanceof ApiKeyAuth) {
        ((ApiKeyAuth) auth).setApiKey(apiKey);
        return;
      }
    }
    throw new RuntimeException("No API key authentication configured!");
  }

  /**
   * Helper method to set API key prefix for the first API key authentication.
   */
  public void setApiKeyPrefix(String apiKeyPrefix) {
    for (Authentication auth : authentications.values()) {
      if (auth instanceof ApiKeyAuth) {
        ((ApiKeyAuth) auth).setApiKeyPrefix(apiKeyPrefix);
        return;
      }
    }
    throw new RuntimeException("No API key authentication configured!");
  }

  /**
   * Set the User-Agent header's value (by adding to the default header map).
   */
  public ApiClient setUserAgent(String userAgent) {
    addDefaultHeader("User-Agent", userAgent);
    return this;
  }

  /**
   * Add a default header.
   *
   * @param key The header's key
   * @param value The header's value
   */
  public ApiClient addDefaultHeader(String key, String value) {
    defaultHeaderMap.put(key, value);
    return this;
  }

  /**
   * @see https://google-gson.googlecode.com/svn/trunk/gson/docs/javadocs/com/google/gson/stream/JsonReader.html#setLenient(boolean)
   */
  public boolean isLenientOnJson() {
    return lenientOnJson;
  }

  public ApiClient setLenientOnJson(boolean lenient) {
    this.lenientOnJson = lenient;
    return this;
  }

  /**
   * Check that whether debugging is enabled for this API client.
   */
  public boolean isDebugging() {
    return debugging;
  }

  /**
   * Enable/disable debugging for this API client.
   *
   * @param debugging To enable (true) or disable (false) debugging
   */
  public ApiClient setDebugging(boolean debugging) {
    this.debugging = debugging;
    return this;
  }

  /**
   * The path of temporary folder used to store downloaded files from endpoints
   * with file response. The default value is <code>null</code>, i.e. using
   * the system's default tempopary folder.
   *
   * @see https://docs.oracle.com/javase/7/docs/api/java/io/File.html#createTempFile(java.lang.String,%20java.lang.String,%20java.io.File)
   */
  public String getTempFolderPath() {
    return tempFolderPath;
  }

  public ApiClient setTempFolderPath(String tempFolderPath) {
    this.tempFolderPath = tempFolderPath;
    return this;
  }

  /**
   * Format the given parameter object into string.
   */
  public String parameterToString(Object param) {
    if (param == null) {
      return "";
    } else if (param instanceof Date) {
      return formatDatetime((Date) param);
    } else if (param instanceof Collection) {
      StringBuilder b = new StringBuilder();
      for (Object o : (Collection)param) {
        if (b.length() > 0) {
          b.append(",");
        }
        b.append(String.valueOf(o));
      }
      return b.toString();
    } else {
      return String.valueOf(param);
    }
  }

  /*
    Format to {@code Pair} objects.
  */
  public List<Pair> parameterToPairs(String collectionFormat, String name, Object value){
    List<Pair> params = new ArrayList<Pair>();

    // preconditions
    if (name == null || name.isEmpty() || value == null) return params;

    Collection valueCollection = null;
    if (value instanceof Collection) {
      valueCollection = (Collection) value;
    } else {
      params.add(new Pair(name, parameterToString(value)));
      return params;
    }

    if (valueCollection.isEmpty()){
      return params;
    }

    // get the collection format
    collectionFormat = (collectionFormat == null || collectionFormat.isEmpty() ? "csv" : collectionFormat); // default: csv

    // create the params based on the collection format
    if (collectionFormat.equals("multi")) {
      for (Object item : valueCollection) {
        params.add(new Pair(name, parameterToString(item)));
      }

      return params;
    }

    String delimiter = ",";

    if (collectionFormat.equals("csv")) {
      delimiter = ",";
    } else if (collectionFormat.equals("ssv")) {
      delimiter = " ";
    } else if (collectionFormat.equals("tsv")) {
      delimiter = "\t";
    } else if (collectionFormat.equals("pipes")) {
      delimiter = "|";
    }

    StringBuilder sb = new StringBuilder() ;
    for (Object item : valueCollection) {
      sb.append(delimiter);
      sb.append(parameterToString(item));
    }

    params.add(new Pair(name, sb.substring(1)));

    return params;
  }

  /**
   * Select the Accept header's value from the given accepts array:
   *   if JSON exists in the given array, use it;
   *   otherwise use all of them (joining into a string)
   *
   * @param accepts The accepts array to select from
   * @return The Accept header to use. If the given array is empty,
   *   null will be returned (not to set the Accept header explicitly).
   */
  public String selectHeaderAccept(String[] accepts) {
    if (accepts.length == 0) return null;
    if (StringUtil.containsIgnoreCase(accepts, "application/json")) return "application/json";
    return StringUtil.join(accepts, ",");
  }

  /**
   * Select the Content-Type header's value from the given array:
   *   if JSON exists in the given array, use it;
   *   otherwise use the first one of the array.
   *
   * @param contentTypes The Content-Type array to select from
   * @return The Content-Type header to use. If the given array is empty,
   *   JSON will be used.
   */
  public String selectHeaderContentType(String[] contentTypes) {
    if (contentTypes.length == 0) return "application/json";
    if (StringUtil.containsIgnoreCase(contentTypes, "application/json")) return "application/json";
    return contentTypes[0];
  }

  /**
   * Escape the given string to be used as URL query value.
   */
  public String escapeString(String str) {
    try {
      return URLEncoder.encode(str, "utf8").replaceAll("\\+", "%20");
    } catch (UnsupportedEncodingException e) {
      return str;
    }
  }

  /**
   * Deserialize response body to Java object, according the Content-Type
   * response header.
   *
   * @param response HTTP response
   * @param returnType The type of the Java object
   * @return The deserialized Java object
   */
  public <T> T deserialize(Response response, Type returnType) throws ApiException {
    if (response == null || returnType == null)
      return null;

    // Handle file downloading.
    if (returnType.equals(File.class))
      return (T) downloadFileFromResponse(response);

    String respBody;
    try {
      if (response.body() != null)
        respBody = response.body().string();
      else
        respBody = null;
    } catch (IOException e) {
      throw new ApiException(e);
    }

    if (respBody == null || "".equals(respBody))
      return null;

    String contentType = response.headers().get("Content-Type");
    if (contentType == null) {
      // ensuring a default content type
      contentType = "application/json";
    }
    if (contentType.startsWith("application/json")) {
      return json.deserialize(respBody, returnType);
    } else {
      throw new ApiException(
        "Content type \"" + contentType + "\" is not supported",
        response.code(),
        response.headers().toMultimap(),
        respBody);
    }
  }

  /**
   * Serialize the given Java object into request body string, according to the
   * request Content-Type.
   *
   * @param obj The Java object
   * @param contentType The request Content-Type
   * @return The serialized string
   */
  public String serialize(Object obj, String contentType) throws ApiException {
    if (contentType.startsWith("application/json")) {
      if (obj != null)
        return json.serialize(obj);
      else
        return null;
    } else {
      throw new ApiException("Content type \"" + contentType + "\" is not supported");
    }
  }

  /**
   * Download file from the given response.
   */
  public File downloadFileFromResponse(Response response) throws ApiException {
    try {
        File file = prepareDownloadFile(response);
        BufferedSink sink = Okio.buffer(Okio.sink(file));
        sink.writeAll(response.body().source());
        sink.close();
        return file;
    } catch (IOException e) {
        throw new ApiException(e);
    }
  }

  public File prepareDownloadFile(Response response) throws IOException {
    String filename = null;
    String contentDisposition = response.header("Content-Disposition");
    if (contentDisposition != null && !"".equals(contentDisposition)) {
      // Get filename from the Content-Disposition header.
      Pattern pattern = Pattern.compile("filename=['\"]?([^'\"\\s]+)['\"]?");
      Matcher matcher = pattern.matcher(contentDisposition);
      if (matcher.find())
        filename = matcher.group(1);
    }

    String prefix = null;
    String suffix = null;
    if (filename == null) {
      prefix = "download-";
      suffix = "";
    } else {
      int pos = filename.lastIndexOf(".");
      if (pos == -1) {
        prefix = filename + "-";
      } else {
        prefix = filename.substring(0, pos) + "-";
        suffix = filename.substring(pos);
      }
      // File.createTempFile requires the prefix to be at least three characters long
      if (prefix.length() < 3)
        prefix = "download-";
    }

    if (tempFolderPath == null)
      return File.createTempFile(prefix, suffix);
    else
      return File.createTempFile(prefix, suffix, new File(tempFolderPath));
  }

  /**
   * @see #execute(Call, Type)
   */
  public <T> T execute(Call call) throws ApiException {
    return execute(call, null);
  }

  /**
   * Execute HTTP call and deserialize the HTTP response body into the given return type.
   *
   * @param returnType The return type used to deserialize HTTP response body
   * @param <T> The return type corresponding to (same with) returnType
   * @return The Java object deserialized from response body. Returns null if returnType is null.
   */
  public <T> T execute(Call call, Type returnType) throws ApiException {
    try {
      Response response = call.execute();
      this.statusCode = response.code();
      this.responseHeaders = response.headers().toMultimap();
      return handleResponse(response, returnType);
    } catch (IOException e) {
      throw new ApiException(e);
    }
  }

  /**
   * #see executeAsync(Call, Type, ApiCallback)
   */
  public <T> void executeAsync(Call call, ApiCallback<T> callback) throws ApiException {
    executeAsync(call, null, callback);
  }

  /**
   * Execute HTTP call asynchronously.
   *
   * @see #execute(Call, Type)
   * @param The callback to be executed when the API call finishes
   */
  public <T> void executeAsync(Call call, final Type returnType, final ApiCallback<T> callback) {
    call.enqueue(new Callback() {
      @Override
      public void onFailure(Request request, IOException e) {
        callback.onFailure(new ApiException(e), 0, null);
      }

      @Override
      public void onResponse(Response response) throws IOException {
        T result;
        try {
          result = (T) handleResponse(response, returnType);
        } catch (ApiException e) {
          callback.onFailure(e, response.code(), response.headers().toMultimap());
          return;
        }
        callback.onSuccess(result, response.code(), response.headers().toMultimap());
      }
    });
  }

  public <T> T handleResponse(Response response, Type returnType) throws ApiException {
    if (response.isSuccessful()) {
      if (returnType == null || response.code() == 204) {
        // returning null if the returnType is not defined,
        // or the status code is 204 (No Content)
        return null;
      } else {
        return deserialize(response, returnType);
      }
    } else {
      String respBody = null;
      if (response.body() != null) {
        try {
          respBody = response.body().string();
        } catch (IOException e) {
          throw new ApiException(response.message(), e, response.code(), response.headers().toMultimap());
        }
      }
      throw new ApiException(response.message(), response.code(), response.headers().toMultimap(), respBody);
    }
  }

  /**
   * Build HTTP call with the given options.
   *
   * @param path The sub-path of the HTTP URL
   * @param method The request method, one of "GET", "HEAD", "OPTIONS", "POST", "PUT", "PATCH" and "DELETE"
   * @param queryParams The query parameters
   * @param body The request body object
   * @param headerParams The header parameters
   * @param formParams The form parameters
   * @param authNames The authentications to apply
   * @return The HTTP call
   */
  public Call buildCall(String path, String method, List<Pair> queryParams, Object body, Map<String, String> headerParams, Map<String, Object> formParams, String[] authNames) throws ApiException {
    updateParamsForAuth(authNames, queryParams, headerParams);

    final String url = buildUrl(path, queryParams);
    final Request.Builder reqBuilder = new Request.Builder().url(url);
    processHeaderParams(headerParams, reqBuilder);

    String contentType = (String) headerParams.get("Content-Type");
    // ensuring a default content type
    if (contentType == null) contentType = "application/json";

    RequestBody reqBody;
    if (!HttpMethod.permitsRequestBody(method)) {
      reqBody = null;
    } else if ("application/x-www-form-urlencoded".equals(contentType)) {
      reqBody = buildRequestBodyFormEncoding(formParams);
    } else if ("multipart/form-data".equals(contentType)) {
      reqBody = buildRequestBodyMultipart(formParams);
    } else if (body == null) {
      if ("DELETE".equals(method)) {
        // allow calling DELETE without sending a request body
        reqBody = null;
      } else {
        // use an empty request body (for POST, PUT and PATCH)
        reqBody = RequestBody.create(MediaType.parse(contentType), "");
      }
    } else {
      reqBody = RequestBody.create(MediaType.parse(contentType), serialize(body, contentType));
    }

    Request request = reqBuilder.method(method, reqBody).build();
    return httpClient.newCall(request);
  }

  /**
   * Build full URL by concatenating base path, the given sub path and query parameters.
   *
   * @param path The sub path
   * @param queryParams The query parameters
   * @return The full URL
   */
  public String buildUrl(String path, List<Pair> queryParams) {
    StringBuilder query = new StringBuilder();
    if (queryParams != null) {
      for (Pair param : queryParams) {
        if (param.getValue() != null) {
          if (query.toString().length() == 0)
            query.append("?");
          else
            query.append("&");
          String value = parameterToString(param.getValue());
          query.append(escapeString(param.getName())).append("=").append(escapeString(value));
        }
      }
    }
    return basePath + path + query.toString();
  }

  /**
   * Set header parameters to the request builder, including default headers.
   */
  public void processHeaderParams(Map<String, String> headerParams, Request.Builder reqBuilder) {
    for (Entry<String, String> param : headerParams.entrySet()) {
      reqBuilder.header(param.getKey(), parameterToString(param.getValue()));
    }
    for (Entry<String, String> header : defaultHeaderMap.entrySet()) {
      if (!headerParams.containsKey(header.getKey())) {
        reqBuilder.header(header.getKey(), parameterToString(header.getValue()));
      }
    }
  }

  /**
   * Update query and header parameters based on authentication settings.
   *
   * @param authNames The authentications to apply
   */
  public void updateParamsForAuth(String[] authNames, List<Pair> queryParams, Map<String, String> headerParams) {
    for (String authName : authNames) {
      Authentication auth = authentications.get(authName);
      if (auth == null) throw new RuntimeException("Authentication undefined: " + authName);
      auth.applyToParams(queryParams, headerParams);
    }
  }

  /**
   * Build a form-encoding request body with the given form parameters.
   */
  public RequestBody buildRequestBodyFormEncoding(Map<String, Object> formParams) {
    FormEncodingBuilder formBuilder  = new FormEncodingBuilder();
    for (Entry<String, Object> param : formParams.entrySet()) {
      formBuilder.add(param.getKey(), parameterToString(param.getValue()));
    }
    return formBuilder.build();
  }

  /**
   * Build a multipart (file uploading) request body with the given form parameters,
   * which could contain text fields and file fields.
   */
  public RequestBody buildRequestBodyMultipart(Map<String, Object> formParams) {
    MultipartBuilder mpBuilder = new MultipartBuilder().type(MultipartBuilder.FORM);
    for (Entry<String, Object> param : formParams.entrySet()) {
      if (param.getValue() instanceof File) {
        File file = (File) param.getValue();
        Headers partHeaders = Headers.of("Content-Disposition", "form-data; name=\"" + param.getKey() + "\"; filename=\"" + file.getName() + "\"");
        MediaType mediaType = MediaType.parse(guessContentTypeFromFile(file));
        mpBuilder.addPart(partHeaders, RequestBody.create(mediaType, file));
      } else {
        Headers partHeaders = Headers.of("Content-Disposition", "form-data; name=\"" + param.getKey() + "\"");
        mpBuilder.addPart(partHeaders, RequestBody.create(null, parameterToString(param.getValue())));
      }
    }
    return mpBuilder.build();
  }

  /**
   * Guess Content-Type header from the given file (defaults to "application/octet-stream").
   *
   * @param file The given file
   * @return The Content-Type guessed
   */
  public String guessContentTypeFromFile(File file) {
    String contentType = URLConnection.guessContentTypeFromName(file.getName());
    if (contentType == null) {
      return "application/octet-stream";
    } else {
      return contentType;
    }
  }
}
