/** 
 * <pre> author: MilkZS time  : 2019/01/09 desc  : https 工具类 </pre>
 */
public final class HttpsUtil {
  private static final int CONNECT_TIMEOUT_TIME=15000;
  private static final int READ_TIMEOUT_TIME=19000;
  /** 
 * POST + JSON
 * @param data send data
 * @param url  target url
 * @return data receive from server
 * @author MilkZS
 */
  public static String postJson(  String data,  String url){
    return doHttpAction(data,true,true,url);
  }
  /** 
 * POST + FORM
 * @param data send data
 * @param url  target url
 * @return data receive from serv
 * @author MilkZS
 */
  public static String postForm(  String data,  String url){
    return doHttpAction(data,false,true,url);
  }
  /** 
 * GET + JSON
 * @param data send data
 * @param url  target url
 * @return data receive from server
 * @author MilkZS
 */
  public static String getJson(  String data,  String url){
    return doHttpAction(data,true,false,url);
  }
  /** 
 * GET + FORM
 * @param data send data
 * @param url  target url
 * @return data receive from server
 * @author MilkZS
 */
  public static String getForm(  String data,  String url){
    return doHttpAction(data,false,false,url);
  }
 private record HttpActionConfig(String url, String body, boolean isJson, boolean isPost) {}

 private static String doHttpAction(HttpActionConfig cfg) {               
    HttpURLConnection connection = null;
    DataOutputStream   os        = null;
    InputStream        is        = null;

    try {
        URL sUrl = new URL(cfg.url());
        connection = (HttpURLConnection) sUrl.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_TIME);
        connection.setReadTimeout(READ_TIMEOUT_TIME);
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod(cfg.isPost() ? "POST" : "GET");

        if (cfg.isJson()) {
            connection.setRequestProperty("Content-Type", "application/json");
        } else {
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length", String.valueOf(cfg.body().length()));
        }

        connection.connect();

        os = new DataOutputStream(connection.getOutputStream());
        os.write(cfg.body().getBytes());
        os.flush();

        is = connection.getInputStream();
        Scanner scan = new Scanner(is).useDelimiter("\\A");
        return scan.hasNext() ? scan.next() : null;

    } catch (Exception e) {
        e.printStackTrace();
        return null;
    } finally {
        if (connection != null) connection.disconnect();
        try { if (os != null) os.close(); } catch (IOException ignored) { }
        try { if (is != null) is.close(); } catch (IOException ignored) { }
    }
 }  
} 
